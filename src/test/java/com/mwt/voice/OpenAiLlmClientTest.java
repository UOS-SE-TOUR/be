package com.mwt.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;

import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Responses API를 흉내 낸 가짜 HTTP 서버로 요청 모양과 응답 해석을 본다. "오늘"은 2026-11-10(화, 서울)이다.
 * 응답 본문은 OpenAI 문서(developers.openai.com Structured Outputs·Responses 레퍼런스)의 모양을 따른다.
 */
class OpenAiLlmClientTest {

	private static final String TEXT = "다음 주 토요일에 골프 그랜드로 둘이 갈래요";

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private MockRestServiceServer server;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	private OpenAiLlmClient client(String apiKey) {
		return new OpenAiLlmClient(restClient, apiKey, jsonMapper, clock);
	}

	private ResponseActions expectRequest() {
		return server.expect(requestTo(OpenAiLlmClient.RESPONSES_URL)).andExpect(method(HttpMethod.POST));
	}

	@Test
	void 설계의_모델을_구조화_출력으로_부르고_오늘_날짜와_요일을_프롬프트에_넣는다() {
		expectRequest()
			.andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""
					{"model": "gpt-6-luna",
					 "input": "%s",
					 "reasoning": {"effort": "low"},
					 "max_output_tokens": 2000,
					 "store": false,
					 "text": {"format": {
					   "type": "json_schema", "name": "tour_selection", "strict": true,
					   "schema": {
					     "type": "object",
					     "properties": {
					       "theme": {"type": ["string", "null"], "enum": ["HONEYMOON", "PARENTS", "GOLF", "TREKKING", null],
					                 "description": "여행 테마"},
					       "departureDate": {"type": ["string", "null"], "description": "출발일(YYYY-MM-DD)"},
					       "level": {"type": ["string", "null"], "enum": ["CLASSIC", "GRAND", "PREMIUM", null],
					                 "description": "투어 등급"},
					       "headcount": {"type": ["integer", "null"], "description": "여행할 사람 수(1 이상)"}},
					     "required": ["theme", "departureDate", "level", "headcount"],
					     "additionalProperties": false}}}}""".formatted(TEXT), JsonCompareMode.LENIENT))
			.andExpect(jsonPath("$.instructions", containsString("오늘 날짜는 2026-11-10(화요일)이다")))
			.andExpect(jsonPath("$.instructions", not(containsString(OpenAiLlmClient.TODAY_PLACEHOLDER))))
			.andRespond(answer("""
					{"theme": "GOLF", "departureDate": "2026-11-21", "level": "GRAND", "headcount": 2}"""));

		TourSelection selection = client("test-key").extractSelection("  " + TEXT + "\n");

		assertThat(selection).isEqualTo(new TourSelection(Theme.GOLF, LocalDate.of(2026, 11, 21), TourLevel.GRAND, 2,
				List.of()));
		server.verify();
	}

	@Test
	void 뽑지_못한_항목은_null로_둔다() {
		expectRequest().andRespond(answer("""
				{"theme": "HONEYMOON", "departureDate": null, "level": null, "headcount": null}"""));

		assertThat(client("test-key").extractSelection("신혼여행 가요"))
			.isEqualTo(TourSelection.extracted(Theme.HONEYMOON, null, null, null));
	}

	@Test
	void 스키마를_벗어난_값은_뽑지_못한_것으로_비운다() {
		expectRequest().andRespond(answer("""
				{"theme": "BEACH", "departureDate": "2026-02-30", "level": "grand", "headcount": 0}"""));

		assertThat(client("test-key").extractSelection(TEXT)).isEqualTo(TourSelection.extracted(null, null, null, null));
	}

	@Test
	void 키가_없으면_OpenAI를_부르지_않고_503() {
		// 기대한 요청이 없으므로 HTTP를 부르면 가짜 서버가 AssertionError를 던져 테스트가 실패한다
		for (String apiKey : new String[] { null, "", " " }) {
			assertUnavailable(() -> client(apiKey).extractSelection(TEXT));
		}
	}

	@Test
	void 키가_틀렸거나_한도를_넘었으면_503() {
		expectRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
			.body("{\"error\": {\"message\": \"Incorrect API key provided\", \"type\": \"invalid_request_error\"}}"));
		assertThatThrownBy(() -> client("wrong-key").extractSelection(TEXT))
			.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
				assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
				assertThat(e.getCause()).isInstanceOf(RestClientResponseException.class);
			});

		server.reset();
		expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertUnavailable(() -> client("test-key").extractSelection(TEXT));
	}

	@Test
	void OpenAI_서버_오류면_503() {
		expectRequest().andRespond(withServerError());

		assertUnavailable(() -> client("test-key").extractSelection(TEXT));
	}

	@Test
	void 시간이_초과되면_503() {
		expectRequest().andRespond(withException(new HttpTimeoutException("request timed out")));

		assertThatThrownBy(() -> client("test-key").extractSelection(TEXT))
			.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
				assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
				assertThat(e.getCause()).isInstanceOf(ResourceAccessException.class);
			});
	}

	@Test
	void 응답이_잘렸으면_503() {
		expectRequest().andRespond(withSuccess("""
				{"id": "resp_1", "object": "response", "status": "incomplete",
				 "incomplete_details": {"reason": "max_output_tokens"},
				 "output": [{"id": "rs_1", "type": "reasoning", "summary": []}]}""", MediaType.APPLICATION_JSON));

		assertUnavailable(() -> client("test-key").extractSelection(TEXT));
	}

	@Test
	void 답을_거절하면_503() {
		expectRequest().andRespond(withSuccess("""
				{"id": "resp_1", "object": "response", "status": "completed", "incomplete_details": null,
				 "output": [{"id": "msg_1", "type": "message", "role": "assistant", "status": "completed",
				   "content": [{"type": "refusal", "refusal": "I'm sorry, I cannot assist with that request."}]}]}""",
				MediaType.APPLICATION_JSON));

		assertUnavailable(() -> client("test-key").extractSelection(TEXT));
	}

	@Test
	void 답_글자가_없거나_JSON이_아니면_503() {
		expectRequest().andRespond(withSuccess("""
				{"id": "resp_1", "object": "response", "status": "completed", "incomplete_details": null,
				 "output": [{"id": "rs_1", "type": "reasoning", "summary": []}]}""", MediaType.APPLICATION_JSON));
		assertUnavailable(() -> client("test-key").extractSelection(TEXT));

		server.reset();
		expectRequest().andRespond(answer("골프로 할게요"));
		assertUnavailable(() -> client("test-key").extractSelection(TEXT));

		server.reset();
		expectRequest().andRespond(withSuccess("<html>Bad Gateway</html>", MediaType.TEXT_HTML));
		assertUnavailable(() -> client("test-key").extractSelection(TEXT));
	}

	@Test
	void 프롬프트_원본에_오늘_자리와_모든_테마와_등급_값이_있다() throws IOException {
		String prompt;
		try (InputStream in = OpenAiLlmClient.class.getResourceAsStream(OpenAiLlmClient.PROMPT_FILE)) {
			assertThat(in).isNotNull();
			prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertThat(prompt).contains(OpenAiLlmClient.TODAY_PLACEHOLDER);
		for (Theme theme : Theme.values()) {
			assertThat(prompt).contains(theme.name() + ": " + theme.getDisplayName());
		}
		for (TourLevel level : TourLevel.values()) {
			assertThat(prompt).contains(level.name() + ": " + level.getDisplayName());
		}
	}

	private static void assertUnavailable(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	/** 추론 항목 뒤에 답 message가 오는 완료 응답. 답 JSON은 output_text의 text에 글자로 든다. */
	private ResponseCreator answer(String outputText) {
		return withSuccess("""
				{"id": "resp_1", "object": "response", "created_at": 1762732800, "status": "completed",
				 "incomplete_details": null, "model": "gpt-6-luna",
				 "output": [
				   {"id": "rs_1", "type": "reasoning", "summary": []},
				   {"id": "msg_1", "type": "message", "role": "assistant", "status": "completed",
				    "content": [{"type": "output_text", "text": %s, "annotations": []}]}],
				 "usage": {"input_tokens": 520, "output_tokens": 64, "total_tokens": 584}}"""
			.formatted(jsonMapper.writeValueAsString(outputText)), MediaType.APPLICATION_JSON);
	}

}
