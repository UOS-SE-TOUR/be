package com.mwt.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class SolapiSmsSenderTest {

	private static final String URL = "https://api.solapi.com/messages/v4/send-many/fast";

	private static final String SALT = "0123456789abcdefghijklmnopqrstuv";

	private static final String ACCEPTED_BODY = """
			{"failedMessageList": [], "groupInfo": {"groupId": "G4V1", "count": {"total": 1, "registeredFailed": 0}}}""";

	private final SolapiProperties properties = new SolapiProperties("test-api-key", "test-api-secret", "010-9999-8888");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	// 밀리초는 서명 시각에서 버린다
	private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneId.of("Asia/Seoul"));

	private MockRestServiceServer server;

	private SolapiSmsSender sender;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		sender = new SolapiSmsSender(builder.build(), properties, jsonMapper, clock, Duration.ZERO, () -> SALT);
	}

	@Test
	void 서명한_요청을_솔라피_형식으로_보낸다() {
		// 서명 기대값은 같은 입력을 Python hmac(SHA-256)·hexdigest로 따로 계산한 값이다
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "HMAC-SHA256 apiKey=test-api-key, "
						+ "date=2023-11-15T07:13:20+09:00, salt=" + SALT + ", "
						+ "signature=9041e056005f394959df13d5b8b08f39d04fc583190055ac1ce70635599a490b"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{"messages": [{"to": "01012345678", "from": "01099998888", "text": "여행 상품이 확정되었습니다"},
						              {"to": "01087654321", "from": "01099998888", "text": "여행 상품이 확정되었습니다"},
						              {"to": "01011112222", "from": "01099998888", "text": "여행 상품이 확정되었습니다"}]}""",
						JsonCompareMode.STRICT))
				.andRespond(accepted());

		// 하이픈·공백은 떼고, +82는 국내 번호로 바꾸고, 같은 번호는 한 번만, 휴대폰 번호 꼴이 아닌 번호(유선 전화,
		// 한 자리 빠진 010 번호)는 빼고 보낸다
		sender.sendSms(List.of("010-1234-5678", "01087654321", "010 1234 5678", "+82 10-1111-2222", "02-123-4567",
				"010-123-4567"), "여행 상품이 확정되었습니다");

		server.verify();
	}

	@Test
	void 문자_종류는_보내지_않아_솔라피가_길이로_정한다() {
		expectRequest().andExpect(jsonPath("$.messages[0].type").doesNotExist()).andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "가".repeat(46));

		server.verify();
	}

	@Test
	void 받는_사람이_100명을_넘으면_나눠_보낸다() {
		List<String> phoneNumbers = IntStream.rangeClosed(1, 101).mapToObj(i -> "010%08d".formatted(i)).toList();
		expectRequest().andExpect(jsonPath("$.messages.length()").value(100)).andRespond(accepted());
		expectRequest().andExpect(jsonPath("$.messages[0].to").value("01000000101")).andRespond(accepted());

		sender.sendSms(phoneNumbers, "확정");

		server.verify();
	}

	@Test
	void 솔라피가_접수하지_않은_번호는_이유와_함께_ERROR로_남기고_다시_보내지_않는다(CapturedOutput output) {
		expectRequest().andRespond(withSuccess("""
				{"failedMessageList": [{"to": "01012345678", "from": "01099998888", "type": "SMS",
				                        "statusCode": "3059", "statusMessage": "변작된 발신번호", "country": "82",
				                        "messageId": "M4V1", "accountId": "A1"}],
				 "groupInfo": {"count": {"total": 2, "registeredFailed": 1}}}""", MediaType.APPLICATION_JSON));

		sender.sendSms(List.of("01012345678", "01087654321"), "확정");

		server.verify();
		assertThat(output).contains("솔라피가 접수하지 않은 문자가 있습니다(1건)")
				.contains("to=01012345678, statusCode=3059, statusMessage=변작된 발신번호")
				.contains("받는 사람 2명, 접수 실패 1명")
				.doesNotContain("한 번 더 보냅니다");
	}

	@Test
	void 첫_시도가_실패하면_한_번_더_보낸다(CapturedOutput output) {
		expectRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		expectRequest().andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "확정");

		server.verify();
		assertThat(output).contains("한 번 더 보냅니다").contains("문자 발송을 요청했습니다").doesNotContain("보내지 못했습니다");
	}

	@Test
	void 연결이_안_되거나_요청이_많다고_하면_한_번_더_보낸다() {
		expectRequest().andRespond(withException(new ConnectException("Connection refused")));
		expectRequest().andRespond(accepted());
		expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		expectRequest().andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "확정");
		sender.sendSms(List.of("01012345678"), "확정");

		server.verify();
	}

	@Test
	void 두_번_다_실패하면_예외_없이_로그만_남긴다(CapturedOutput output) {
		server.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatCode(() -> sender.sendSms(List.of("01012345678"), "확정")).doesNotThrowAnyException();

		server.verify();
		assertThat(output).contains("문자를 보내지 못했습니다(시도 2번)").contains("01012345678");
	}

	@Test
	void 솔라피가_받았을_수_있거나_다시_보내도_안_되는_실패는_다시_보내지_않는다(CapturedOutput output) {
		// 응답을 못 받았거나(읽기 시간 초과) 503 밖의 5xx면 솔라피가 이미 받았을 수 있다(다시 보내면 문자가 두 번 간다).
		// 4xx는 키·서명·요청 오류라 다시 보내도 실패한다
		expectRequest().andRespond(withException(new HttpTimeoutException("request timed out")));
		expectRequest().andRespond(withServerError());
		expectRequest().andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
		expectRequest().andRespond(withBadRequest());

		assertThatCode(() -> {
			for (int i = 0; i < 4; i++) {
				sender.sendSms(List.of("01012345678"), "확정");
			}
		}).doesNotThrowAnyException();

		server.verify();
		assertThat(output).contains("문자를 보내지 못했습니다(시도 1번)").doesNotContain("한 번 더 보냅니다");
	}

	@Test
	void 응답_본문을_읽지_못하면_접수_여부를_모른다고_ERROR로_남기고_다시_보내지_않는다(CapturedOutput output) {
		expectRequest().andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
		expectRequest().andRespond(withStatus(HttpStatus.OK));

		assertThatCode(() -> {
			sender.sendSms(List.of("01012345678"), "확정");
			sender.sendSms(List.of("01012345678"), "확정");
		}).doesNotThrowAnyException();

		server.verify();
		assertThat(output).contains("ERROR")
				.contains("응답을 읽지 못해 접수됐는지 알 수 없습니다: phoneNumbers=[01012345678], 응답={")
				.contains("응답을 읽지 못해 접수됐는지 알 수 없습니다: phoneNumbers=[01012345678], 응답=null")
				.doesNotContain("한 번 더 보냅니다");
	}

	@Test
	void 다시_보낼_때는_새_salt로_서명한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer otherServer = MockRestServiceServer.bindTo(builder).build();
		List<String> authorizations = new ArrayList<>();
		otherServer.expect(requestTo(URL))
				.andExpect(request -> authorizations.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		otherServer.expect(requestTo(URL))
				.andExpect(request -> authorizations.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
				.andRespond(accepted());
		AtomicInteger count = new AtomicInteger();
		SolapiSmsSender countingSalts = new SolapiSmsSender(builder.build(), properties, jsonMapper, clock, Duration.ZERO,
				() -> "salt%028d".formatted(count.incrementAndGet()));

		countingSalts.sendSms(List.of("01012345678"), "확정");

		otherServer.verify();
		assertThat(authorizations).hasSize(2).doesNotHaveDuplicates();
		assertThat(authorizations.get(1)).contains("salt=salt0000000000000000000000000002");
	}

	@Test
	void 발신_번호도_국내_번호로_바꿔_보낸다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer otherServer = MockRestServiceServer.bindTo(builder).build();
		SolapiProperties international = new SolapiProperties("test-api-key", "test-api-secret", "+82 10-9999-8888");
		otherServer.expect(requestTo(URL)).andExpect(jsonPath("$.messages[0].from").value("01099998888"))
				.andRespond(accepted());

		new SolapiSmsSender(builder.build(), international, jsonMapper, clock, Duration.ZERO, () -> SALT)
				.sendSms(List.of("01012345678"), "확정");

		otherServer.verify();
	}

	@Test
	void 요청마다_새_salt로_서명한다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer otherServer = MockRestServiceServer.bindTo(builder).build();
		String authorization = "HMAC-SHA256 apiKey=test-api-key, date=2023-11-15T07:13:20\\+09:00, "
				+ "salt=[0-9a-zA-Z]{32}, signature=[0-9a-f]{64}";
		List<String> authorizations = new ArrayList<>();
		otherServer.expect(ExpectedCount.twice(), requestTo(URL))
				.andExpect(header(HttpHeaders.AUTHORIZATION, Matchers.matchesPattern(authorization)))
				.andExpect(request -> authorizations.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
				.andRespond(accepted());
		SolapiSmsSender randomSalted = new SolapiSmsSender(builder.build(), properties, jsonMapper, clock, Duration.ZERO);

		randomSalted.sendSms(List.of("01012345678"), "확정");
		randomSalted.sendSms(List.of("01012345678"), "확정");

		otherServer.verify();
		assertThat(authorizations).hasSize(2).doesNotHaveDuplicates();
	}

	@Test
	void 재시도_대기가_인터럽트되면_남은_묶음을_보내지_않는다(CapturedOutput output) {
		List<String> phoneNumbers = IntStream.rangeClosed(1, 101).mapToObj(i -> "010%08d".formatted(i)).toList();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer otherServer = MockRestServiceServer.bindTo(builder).build();
		// 첫 묶음이 503을 받는 사이 서버 종료 등으로 인터럽트가 걸린다 → 재시도 대기(1ms)가 끊긴다
		otherServer.expect(requestTo(URL)).andRespond(request -> {
			Thread.currentThread().interrupt();
			return withStatus(HttpStatus.SERVICE_UNAVAILABLE).createResponse(request);
		});
		SolapiSmsSender waitingSender = new SolapiSmsSender(builder.build(), properties, jsonMapper, clock,
				Duration.ofMillis(1), () -> SALT);

		try {
			waitingSender.sendSms(phoneNumbers, "확정");
		} finally {
			Thread.interrupted();
		}

		otherServer.verify();
		assertThat(output).contains("문자 발송이 중단되어 보내지 못했습니다: phoneNumbers=[01000000101]");
	}

	@Test
	void 받는_번호나_내용이_없으면_보내지_않는다() {
		sender.sendSms(List.of(), "확정");
		sender.sendSms(List.of("-", ""), "확정");
		sender.sendSms(List.of("01012345678"), " ");

		server.verify();
	}

	private ResponseActions expectRequest() {
		return server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST));
	}

	private static ResponseCreator accepted() {
		return withSuccess(ACCEPTED_BODY, MediaType.APPLICATION_JSON);
	}

}
