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

import com.mwt.tour.OptionCategory;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourStatus;

import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Responses API를 흉내 낸 가짜 HTTP 서버로 요청 모양과 응답 해석을 본다. "오늘"은 2026-11-10(화, 서울)이다.
 * 응답 본문은 OpenAI 문서(developers.openai.com Structured Outputs·Responses 레퍼런스)의 모양을 따른다.
 */
class OpenAiLlmClientTest {

	private static final List<VoiceMessage> MESSAGES = List.of(
			new VoiceMessage(VoiceSpeaker.SYSTEM, "안녕하세요, 최유성 고객님. 어떤 여행을 찾으세요?"),
			new VoiceMessage(VoiceSpeaker.CUSTOMER, "부모님 결혼기념일이에요"));

	private static final VoiceContext CONTEXT = new VoiceContext(List.of(new VoiceContext.Tour(12L, Theme.PARENTS,
			"효도 힐링 투어 12/5 출발 5일", LocalDate.of(2026, 12, 5), 5, 8, TourStatus.RECRUITING,
			List.of(TourLevel.GRAND, TourLevel.PREMIUM),
			List.of(new VoiceContext.Option(105L, OptionCategory.MEAL, "고급 레스토랑")))),
			new VoiceDraft(Theme.PARENTS, null, null, null, List.of()));

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
	void 설계의_모델을_구조화_출력으로_부르고_대화는_역할을_붙여_input에_넣는다() {
		expectRequest()
			.andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""
					{"model": "gpt-6-luna",
					 "input": [{"role": "assistant", "content": "안녕하세요, 최유성 고객님. 어떤 여행을 찾으세요?"},
					           {"role": "user", "content": "부모님 결혼기념일이에요"}],
					 "reasoning": {"effort": "low"},
					 "max_output_tokens": 4000,
					 "store": false,
					 "text": {"format": {
					   "type": "json_schema", "name": "voice_chat", "strict": true,
					   "schema": {
					     "type": "object",
					     "properties": {
					       "reply": {"type": "string", "description": "고객에게 할 말"},
					       "theme": {"type": ["string", "null"], "enum": ["HONEYMOON", "PARENTS", "GOLF", "TREKKING", null],
					                 "description": "테마"},
					       "tourId": {"type": ["integer", "null"], "description": "상품 ID(tours의 tourId)"},
					       "level": {"type": ["string", "null"], "enum": ["CLASSIC", "GRAND", "PREMIUM", null],
					                 "description": "투어 등급"},
					       "headcount": {"type": ["integer", "null"], "description": "인원(명)"},
					       "optionIds": {"type": "array", "items": {"type": "integer"},
					                     "description": "고른 옵션의 ID(그 상품의 options의 optionId)"},
					       "next": {"type": "string", "enum": ["TALK", "APPLY", "STOP"], "description": "다음에 할 일"}},
					     "required": ["reply", "theme", "tourId", "level", "headcount", "optionIds", "next"],
					     "additionalProperties": false}}}}""", JsonCompareMode.LENIENT))
			.andExpect(jsonPath("$.input.length()").value(2))
			.andRespond(answer("""
					{"reply": "효도 힐링 투어를 권해 드려요. 12월 5일 출발 5일짜리가 있습니다.", "theme": "PARENTS", "tourId": 12,
					 "level": "GRAND", "headcount": 3, "optionIds": [105], "next": "TALK"}"""));

		VoiceChatResponse response = client("test-key").chat(CONTEXT, MESSAGES);

		assertThat(response).isEqualTo(new VoiceChatResponse("효도 힐링 투어를 권해 드려요. 12월 5일 출발 5일짜리가 있습니다.",
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3, List.of(105L)), VoiceNext.TALK));
		server.verify();
	}

	@Test
	void 프롬프트에_오늘_날짜와_요일_상품_목록과_지금_고른_내용을_넣고_가격은_넣지_않는다() {
		expectRequest()
			.andExpect(jsonPath("$.instructions", containsString("오늘 날짜는 2026-11-10(화요일)이다")))
			.andExpect(jsonPath("$.instructions", containsString("""
					{"tours":[{"tourId":12,"theme":"PARENTS","title":"효도 힐링 투어 12/5 출발 5일","departureDate":"2026-12-05",\
					"duration":5,"remainingSeats":8,"status":"RECRUITING","levels":["GRAND","PREMIUM"],\
					"options":[{"optionId":105,"category":"MEAL","name":"고급 레스토랑"}]}],\
					"draft":{"theme":"PARENTS","tourId":null,"level":null,"headcount":null,"optionIds":[]}}""")))
			.andExpect(jsonPath("$.instructions", not(containsString(OpenAiLlmClient.TODAY_PLACEHOLDER))))
			.andExpect(jsonPath("$.instructions", not(containsString(OpenAiLlmClient.CONTEXT_PLACEHOLDER))))
			.andExpect(jsonPath("$.instructions", not(containsString("price"))))
			.andRespond(answer("""
					{"reply": "네.", "theme": null, "tourId": null, "level": null, "headcount": null, "optionIds": [],
					 "next": "TALK"}"""));

		client("test-key").chat(CONTEXT, MESSAGES);

		server.verify();
	}

	@Test
	void 고르지_않은_칸은_null이고_옵션은_빈_목록이다() {
		expectRequest().andRespond(answer("""
				{"reply": "어떤 테마를 찾으세요?", "theme": null, "tourId": null, "level": null, "headcount": null,
				 "optionIds": [], "next": "TALK"}"""));

		assertThat(client("test-key").chat(CONTEXT, MESSAGES))
			.isEqualTo(new VoiceChatResponse("어떤 테마를 찾으세요?", VoiceDraft.EMPTY, VoiceNext.TALK));
	}

	@Test
	void 스키마를_벗어난_테마와_등급은_비운다() {
		expectRequest().andRespond(answer("""
				{"reply": "네.", "theme": "BEACH", "tourId": null, "level": "grand", "headcount": 0, "optionIds": null,
				 "next": "STOP"}"""));

		assertThat(client("test-key").chat(CONTEXT, MESSAGES))
			.isEqualTo(new VoiceChatResponse("네.", new VoiceDraft(null, null, null, 0, List.of()), VoiceNext.STOP));
	}

	@Test
	void 할_말이_비었거나_다음에_할_일을_읽을_수_없으면_503() {
		for (String reply : new String[] { "null", "\"\"", "\"  \\n\"" }) {
			server.reset();
			expectRequest().andRespond(answer("""
					{"reply": %s, "theme": null, "tourId": null, "level": null, "headcount": null, "optionIds": [],
					 "next": "TALK"}""".formatted(reply)));
			assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
		}
		for (String next : new String[] { "null", "\"talk\"", "\"DONE\"" }) {
			server.reset();
			expectRequest().andRespond(answer("""
					{"reply": "네.", "theme": null, "tourId": null, "level": null, "headcount": null, "optionIds": [],
					 "next": %s}""".formatted(next)));
			assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
		}
	}

	@Test
	void 키가_없으면_OpenAI를_부르지_않고_503() {
		// 기대한 요청이 없으므로 HTTP를 부르면 가짜 서버가 AssertionError를 던져 테스트가 실패한다
		for (String apiKey : new String[] { null, "", " " }) {
			assertUnavailable(() -> client(apiKey).chat(CONTEXT, MESSAGES));
		}
	}

	@Test
	void 키가_틀렸거나_한도를_넘었으면_503() {
		expectRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
			.body("{\"error\": {\"message\": \"Incorrect API key provided\", \"type\": \"invalid_request_error\"}}"));
		assertThatThrownBy(() -> client("wrong-key").chat(CONTEXT, MESSAGES))
			.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
				assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
				assertThat(e.getCause()).isInstanceOf(RestClientResponseException.class);
			});

		server.reset();
		expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
	}

	@Test
	void OpenAI_서버_오류면_503() {
		expectRequest().andRespond(withServerError());

		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
	}

	@Test
	void 시간이_초과되면_503() {
		expectRequest().andRespond(withException(new HttpTimeoutException("request timed out")));

		assertThatThrownBy(() -> client("test-key").chat(CONTEXT, MESSAGES))
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

		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
	}

	@Test
	void 답을_거절하면_503() {
		expectRequest().andRespond(withSuccess("""
				{"id": "resp_1", "object": "response", "status": "completed", "incomplete_details": null,
				 "output": [{"id": "msg_1", "type": "message", "role": "assistant", "status": "completed",
				   "content": [{"type": "refusal", "refusal": "I'm sorry, I cannot assist with that request."}]}]}""",
				MediaType.APPLICATION_JSON));

		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
	}

	@Test
	void 답_글자가_없거나_JSON이_아니면_503() {
		expectRequest().andRespond(withSuccess("""
				{"id": "resp_1", "object": "response", "status": "completed", "incomplete_details": null,
				 "output": [{"id": "rs_1", "type": "reasoning", "summary": []}]}""", MediaType.APPLICATION_JSON));
		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));

		server.reset();
		expectRequest().andRespond(answer("골프로 할게요"));
		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));

		server.reset();
		expectRequest().andRespond(withSuccess("<html>Bad Gateway</html>", MediaType.TEXT_HTML));
		assertUnavailable(() -> client("test-key").chat(CONTEXT, MESSAGES));
	}

	@Test
	void 프롬프트_원본에_자리_표시와_모든_테마_등급_다음에_할_일_값이_있다() throws IOException {
		String prompt;
		try (InputStream in = OpenAiLlmClient.class.getResourceAsStream(OpenAiLlmClient.PROMPT_FILE)) {
			assertThat(in).isNotNull();
			prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertThat(prompt).contains(OpenAiLlmClient.TODAY_PLACEHOLDER, OpenAiLlmClient.CONTEXT_PLACEHOLDER);
		for (Theme theme : Theme.values()) {
			assertThat(prompt).contains(theme.name() + ": " + theme.getDisplayName());
		}
		// 등급이 기본으로 주는 것은 프롬프트에 손으로 적었으니 열거형과 같은지 본다
		for (TourLevel level : TourLevel.values()) {
			assertThat(prompt).contains(level.name() + ": " + level.getDisplayName() + "(" + level.getHotelGrade() + "등급 호텔, "
					+ level.getMeal() + (level.isIncludesChampagne() ? ", 샴페인" : "") + ")");
		}
		for (VoiceNext next : VoiceNext.values()) {
			assertThat(prompt).contains(next.name());
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
