package com.mwt.notification;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * NCP SENS 문자 발송 API(POST /sms/v2/services/{serviceId}/messages)로 문자를 보낸다.
 * 실패하면 한 번 더 보내고, 그래도 실패하면 서버 로그에 남긴다(설계산출물 v0.4 1.3 (8)). 다시 보내는 경우는 isRetryable에 있다.
 */
@Slf4j
class SensSmsSender implements SmsSender {

	static final String API_HOST = "https://sens.apigw.ntruss.com";

	/** 처음 보낸 뒤 실패하면 더 보내는 횟수(설계산출물 v0.4 4.6 retryCount = 1). */
	private static final int RETRY_COUNT = 1;

	/** 한 요청에 넣을 수 있는 받는 사람 수(SENS messages 최대 100건). */
	static final int MAX_RECIPIENTS = 100;

	/** SMS 본문 한도. 넘으면 LMS(2000바이트까지)로 보낸다. SENS는 EUC-KR 바이트로 센다. */
	private static final int SMS_MAX_BYTES = 90;

	private static final Charset EUC_KR = Charset.forName("EUC-KR");

	private final RestClient restClient;
	private final SensProperties properties;
	private final Clock clock;

	SensSmsSender(RestClient restClient, SensProperties properties, Clock clock) {
		this.restClient = restClient;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void sendSms(List<String> phoneNumbers, String message) {
		try {
			List<String> recipients = phoneNumbers.stream()
					.filter(Objects::nonNull)
					.map(SensSmsSender::digitsOnly)
					.filter(number -> !number.isEmpty())
					.distinct()
					.toList();
			if (recipients.isEmpty() || message == null || message.isBlank()) {
				log.warn("받는 번호나 문자 내용이 없어 문자를 보내지 않았습니다: phoneNumbers={}", phoneNumbers);
				return;
			}
			for (int from = 0; from < recipients.size(); from += MAX_RECIPIENTS) {
				sendWithRetry(recipients.subList(from, Math.min(from + MAX_RECIPIENTS, recipients.size())), message);
			}
		}
		catch (RuntimeException e) {
			log.error("문자를 보내지 못했습니다: phoneNumbers={}, message={}", phoneNumbers, message, e);
		}
	}

	private void sendWithRetry(List<String> recipients, String message) {
		for (int attempt = 1;; attempt++) {
			try {
				send(recipients, message);
				log.info("문자 발송을 요청했습니다: 받는 사람 {}명", recipients.size());
				return;
			}
			catch (RuntimeException e) {
				if (attempt <= RETRY_COUNT && isRetryable(e)) {
					log.warn("문자 발송에 실패해 한 번 더 보냅니다: {}", e.toString());
					continue;
				}
				log.error("문자를 보내지 못했습니다(시도 {}번): phoneNumbers={}, message={}", attempt, recipients, message, e);
				return;
			}
		}
	}

	/**
	 * SENS에 닿지 못했거나(연결 실패) SENS가 처리하지 못했다고 답했을 때(5xx·429)만 다시 보낸다.
	 * 응답을 못 받은 경우(읽기 시간 초과)는 SENS가 이미 받았을 수 있어 같은 문자가 두 번 가지 않게 다시 보내지 않고,
	 * 다른 4xx(키·번호 오류)는 다시 보내도 실패한다.
	 */
	private static boolean isRetryable(RuntimeException e) {
		if (e instanceof RestClientResponseException response) {
			return response.getStatusCode().is5xxServerError()
					|| response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
		}
		if (e instanceof ResourceAccessException) {
			Throwable cause = e.getCause();
			return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
					|| cause instanceof UnknownHostException;
		}
		return false;
	}

	private void send(List<String> recipients, String message) {
		String path = "/sms/v2/services/" + properties.serviceId() + "/messages";
		String timestamp = String.valueOf(clock.millis());
		SendRequest request = new SendRequest(typeOf(message), digitsOnly(properties.senderNumber()), message,
				recipients.stream().map(Recipient::new).toList());
		// URI로 넘겨 경로를 다시 인코딩하지 않는다 — 서명한 경로와 보내는 경로가 같아야 한다(서비스 ID에 ':'가 든다)
		// 응답 본문은 읽지 않는다 — 2xx면 SENS가 받은 것이고, 본문 해석이 실패했다고 다시 보내면 문자가 두 번 간다
		restClient.post()
				.uri(URI.create(API_HOST + path))
				.contentType(MediaType.APPLICATION_JSON)
				.header("x-ncp-apigw-timestamp", timestamp)
				.header("x-ncp-iam-access-key", properties.accessKey())
				.header("x-ncp-apigw-signature-v2", sign("POST " + path + "\n" + timestamp + "\n" + properties.accessKey()))
				.body(request)
				.retrieve()
				.toBodilessEntity();
	}

	/** NCP API 서명: HmacSHA256(Secret Key)을 Base64로. */
	private String sign(String text) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("SENS 요청 서명을 만들지 못했습니다", e);
		}
	}

	/** EUC-KR로 못 쓰는 글자(이모지 등)가 있으면 바이트 수를 알 수 없으니 LMS로 보낸다. */
	private static String typeOf(String message) {
		boolean fitsSms = EUC_KR.newEncoder().canEncode(message) && message.getBytes(EUC_KR).length <= SMS_MAX_BYTES;
		return fitsSms ? "SMS" : "LMS";
	}

	private static String digitsOnly(String phoneNumber) {
		return phoneNumber.replaceAll("[^0-9]", "");
	}

	record SendRequest(String type, String from, String content, List<Recipient> messages) {
	}

	record Recipient(String to) {
	}

}
