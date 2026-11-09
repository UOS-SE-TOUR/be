package com.mwt.notification;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 솔라피 문자 발송 API(POST /messages/v4/send-many/fast)로 문자를 보낸다.
 * 실패하면 한 번 더 보내고, 그래도 실패하면 서버 로그에 남긴다(설계산출물 v0.5 1.3 (8)). 다시 보내는 경우는 isRetryable에 있다.
 * 문자 종류(SMS·LMS)는 보내지 않아 솔라피가 내용 길이로 정한다.
 */
@Slf4j
class SolapiSmsSender implements SmsSender {

	static final String SEND_URL = "https://api.solapi.com/messages/v4/send-many/fast";

	/** 처음 보낸 뒤 실패하면 더 보내는 횟수(설계산출물 v0.5 4.6 retryCount = 1). */
	private static final int RETRY_COUNT = 1;

	/**
	 * 한 요청에 넣는 받는 사람 수. 솔라피는 10,000건까지 받지만, 읽기 시간 초과는 다시 보내지 않아 그 묶음의 접수 여부를 알 수 없게
	 * 되므로 한 번에 그렇게 되는 사람 수를 줄인다.
	 */
	static final int MAX_RECIPIENTS = 100;

	private static final String SALT_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private static final int SALT_LENGTH = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RestClient restClient;
	private final SolapiProperties properties;
	private final String senderNumber;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final Duration retryDelay;
	private final Supplier<String> salts;

	SolapiSmsSender(RestClient restClient, SolapiProperties properties, JsonMapper jsonMapper, Clock clock,
			Duration retryDelay) {
		this(restClient, properties, jsonMapper, clock, retryDelay, SolapiSmsSender::randomSalt);
	}

	/** salts는 테스트가 서명을 재현하려고 바꿔 넣는다. */
	SolapiSmsSender(RestClient restClient, SolapiProperties properties, JsonMapper jsonMapper, Clock clock,
			Duration retryDelay, Supplier<String> salts) {
		this.restClient = restClient;
		this.properties = properties;
		this.senderNumber = PhoneNumbers.normalize(properties.senderNumber());
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.retryDelay = retryDelay;
		this.salts = salts;
	}

	@Override
	public void sendSms(List<String> phoneNumbers, String message) {
		try {
			PhoneNumbers.Recipients numbers = PhoneNumbers.classify(phoneNumbers);
			if (!numbers.rejected().isEmpty()) {
				log.warn("휴대폰 번호 꼴이 아닌 번호는 빼고 보냅니다: {}", numbers.rejected());
			}
			List<String> recipients = numbers.mobile();
			if (recipients.isEmpty() || message == null || message.isBlank()) {
				log.warn("받는 번호나 문자 내용이 없어 문자를 보내지 않았습니다: phoneNumbers={}", phoneNumbers);
				return;
			}
			for (int from = 0; from < recipients.size(); from += MAX_RECIPIENTS) {
				if (Thread.currentThread().isInterrupted()) {   // 서버 종료 등으로 재시도 대기가 끊겼다
					log.error("문자 발송이 중단되어 보내지 못했습니다: phoneNumbers={}, message={}",
							recipients.subList(from, recipients.size()), message);
					return;
				}
				sendWithRetry(recipients.subList(from, Math.min(from + MAX_RECIPIENTS, recipients.size())), message);
			}
		} catch (RuntimeException e) {
			log.error("문자를 보내지 못했습니다: phoneNumbers={}, message={}", phoneNumbers, message, e);
		}
	}

	private void sendWithRetry(List<String> recipients, String message) {
		for (int attempt = 1;; attempt++) {
			String responseBody;
			try {
				responseBody = send(recipients, message);
			} catch (RuntimeException e) {
				if (attempt <= RETRY_COUNT && isRetryable(e)) {
					log.warn("문자 발송에 실패해 한 번 더 보냅니다: {}", e.toString());
					if (waitBeforeRetry()) {
						continue;
					}
				}
				log.error("문자를 보내지 못했습니다(시도 {}번): phoneNumbers={}, message={}", attempt, recipients, message, e);
				return;
			}
			logResult(recipients, responseBody);
			return;
		}
	}

	/**
	 * 솔라피는 요청을 받으면 200을 돌려주고, 그중 접수하지 못한 메시지(발신 번호 미등록, 잔액 부족 등)를 failedMessageList로 알린다.
	 * 그래서 200이어도 실패한 번호가 있으면 이유와 함께 ERROR로 남긴다. 본문을 읽지 못하면 접수됐는지 알 수 없으니 그것도 ERROR로
	 * 남기고, 솔라피가 이미 받았을 수 있어 다시 보내지는 않는다(다시 보내면 문자가 두 번 간다).
	 */
	private void logResult(List<String> recipients, String responseBody) {
		SendResponse response;
		try {
			response = responseBody == null ? null : jsonMapper.readValue(responseBody, SendResponse.class);
		} catch (JacksonException e) {
			response = null;
		}
		if (response == null) {
			log.error("문자 발송은 요청했지만 응답을 읽지 못해 접수됐는지 알 수 없습니다: phoneNumbers={}, 응답={}", recipients,
					responseBody);
			return;
		}
		List<FailedMessage> failed = response.failedMessageList() == null ? List.of() : response.failedMessageList();
		if (!failed.isEmpty()) {
			log.error("솔라피가 접수하지 않은 문자가 있습니다({}건): {}", failed.size(), failed);
		}
		log.info("문자 발송을 요청했습니다: 받는 사람 {}명, 접수 실패 {}명", recipients.size(), failed.size());
	}

	/**
	 * 솔라피가 받지 않은 게 분명할 때만 다시 보낸다 — 연결 실패, 429(요청 과다), 503(서비스 불가).
	 * 그 밖의 5xx와 읽기 시간 초과는 솔라피가 이미 받았을 수 있어 같은 문자가 두 번 가지 않게 다시 보내지 않고,
	 * 다른 4xx(키·서명·요청 오류)는 다시 보내도 실패한다.
	 */
	private static boolean isRetryable(RuntimeException e) {
		if (e instanceof RestClientResponseException response) {
			HttpStatusCode status = response.getStatusCode();
			return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) || status.isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE);
		}
		if (e instanceof ResourceAccessException) {
			Throwable cause = e.getCause();
			return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
		}
		return false;
	}

	/** 요청 과다(429)·일시 장애가 풀리도록 잠깐 기다린다. 기다리는 중 인터럽트되면 다시 보내지 않는다. */
	private boolean waitBeforeRetry() {
		try {
			Thread.sleep(retryDelay);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** 응답 본문은 문자열로 받아 logResult가 읽는다 — 여기서 나는 예외는 요청·응답 단계의 것만 남게 한다. */
	private String send(List<String> recipients, String message) {
		SendRequest request = new SendRequest(recipients.stream().map(to -> new Message(to, senderNumber, message)).toList());
		return restClient.post()
				.uri(URI.create(SEND_URL))
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.body(request)
				.retrieve()
				.body(String.class);
	}

	/**
	 * 솔라피 API Key 인증: date와 salt를 이어 붙인 글을 API Secret으로 HmacSHA256한 16진수가 서명이다.
	 * 솔라피는 한 번 쓴 서명을 다시 받지 않으므로(403) 다시 보낼 때도 새로 만든다.
	 */
	private String authorization() {
		String date = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		String salt = salts.get();
		return "HMAC-SHA256 apiKey=" + properties.apiKey() + ", date=" + date + ", salt=" + salt + ", signature="
				+ sign(date + salt);
	}

	private String sign(String text) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("솔라피 요청 서명을 만들지 못했습니다", e);
		}
	}

	/** 요청마다 새로 만드는 영문·숫자 32자. */
	static String randomSalt() {
		StringBuilder salt = new StringBuilder(SALT_LENGTH);
		for (int i = 0; i < SALT_LENGTH; i++) {
			salt.append(SALT_CHARACTERS.charAt(RANDOM.nextInt(SALT_CHARACTERS.length())));
		}
		return salt.toString();
	}

	record SendRequest(List<Message> messages) {
	}

	record Message(String to, String from, String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SendResponse(List<FailedMessage> failedMessageList) {
	}

	/** 접수하지 못한 메시지 하나. statusCode·statusMessage가 이유다(예: 3059 변작된 발신번호). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record FailedMessage(String to, String statusCode, String statusMessage) {
	}

}
