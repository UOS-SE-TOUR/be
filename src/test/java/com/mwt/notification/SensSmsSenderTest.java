package com.mwt.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class SensSmsSenderTest {

	private static final String URL = "https://sens.apigw.ntruss.com/sms/v2/services/ncp:sms:kr:123456789012:mwt/messages";

	private static final String ACCEPTED_BODY = """
			{"requestId":"RSLA-1","requestTime":"2026-11-10T09:00:00.000","statusCode":"202","statusName":"success"}""";

	private final SensProperties properties = new SensProperties("test-access-key", "test-secret-key",
			"ncp:sms:kr:123456789012:mwt", "02-1234-5678");

	private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.of("Asia/Seoul"));

	private MockRestServiceServer server;

	private SensSmsSender sender;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		sender = new SensSmsSender(builder.build(), properties, clock);
	}

	@Test
	void 서명한_요청을_SENS_형식으로_보낸다() {
		// 서명 기대값은 같은 입력을 Python hmac(SHA-256)·base64로 따로 계산한 값이다
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-ncp-apigw-timestamp", "1700000000000"))
				.andExpect(header("x-ncp-iam-access-key", "test-access-key"))
				.andExpect(header("x-ncp-apigw-signature-v2", "hZfUWnO4P7shi/qpj+c/BcppDD5/mtmnMHTgv6qwF/U="))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{"type": "SMS", "from": "0212345678", "content": "여행 상품이 확정되었습니다",
						 "messages": [{"to": "01012345678"}, {"to": "01087654321"}]}""", JsonCompareMode.STRICT))
				.andRespond(accepted());

		sender.sendSms(List.of("010-1234-5678", "01087654321", "010 1234 5678"), "여행 상품이 확정되었습니다");

		server.verify();
	}

	@Test
	void 본문이_90바이트를_넘으면_LMS로_보낸다() {
		expectRequest().andExpect(jsonPath("$.type").value("SMS")).andRespond(accepted());
		expectRequest().andExpect(jsonPath("$.type").value("LMS")).andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "가".repeat(45));
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
	void 첫_시도가_실패하면_한_번_더_보낸다(CapturedOutput output) {
		expectRequest().andRespond(withServerError());
		expectRequest().andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "확정");

		server.verify();
		assertThat(output).contains("한 번 더 보냅니다").contains("requestId=RSLA-1").doesNotContain("2번 실패");
	}

	@Test
	void 응답이_없어_실패해도_한_번_더_보낸다() {
		expectRequest().andRespond(withException(new SocketTimeoutException("Read timed out")));
		expectRequest().andRespond(accepted());

		sender.sendSms(List.of("01012345678"), "확정");

		server.verify();
	}

	@Test
	void 두_번_다_실패하면_예외_없이_로그만_남긴다(CapturedOutput output) {
		server.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withServerError());

		assertThatCode(() -> sender.sendSms(List.of("01012345678"), "확정")).doesNotThrowAnyException();

		server.verify();
		assertThat(output).contains("문자 발송에 2번 실패했습니다").contains("01012345678");
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

	private static org.springframework.test.web.client.ResponseCreator accepted() {
		return withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(ACCEPTED_BODY);
	}

}
