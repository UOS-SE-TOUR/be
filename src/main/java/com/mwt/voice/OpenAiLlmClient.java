package com.mwt.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mwt.common.text.TextInput;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI API(Responses API, POST /v1/responses)를 구조화 출력(JSON 스키마)으로 불러 음성 대화의 답을 받는다
 * (설계산출물 4.7, 5.5 responses.create(prompt, schema)). 프롬프트 원본은 이 클래스 옆의 {@value #PROMPT_FILE} 하나이고,
 * 오늘 날짜와 상품 목록·지금 고른 내용(VoiceContext의 JSON)을 넣어 instructions로 보낸다. 대화는 input에 역할을 붙여 보낸다.
 */
@Slf4j
class OpenAiLlmClient implements LlmClient {

	static final String RESPONSES_URL = "https://api.openai.com/v1/responses";

	/** 설계산출물 4.7 MODEL. */
	static final String MODEL = "gpt-6-luna";

	static final String PROMPT_FILE = "chat-prompt.txt";

	static final String TODAY_PLACEHOLDER = "{{today}}";

	static final String CONTEXT_PLACEHOLDER = "{{context}}";

	/** 정해 둔 순서대로 묻고 상품 목록에서 고르는 정도라 추론을 낮게 둔다. */
	private static final String REASONING_EFFORT = "low";

	/** 추론 토큰까지 센 응답 한도. 답 JSON은 할 말(두 문장 안팎)까지 넣어도 300토큰 안팎이고 나머지는 추론 몫이다. */
	private static final int MAX_OUTPUT_TOKENS = 4000;

	private static final Map<String, Object> SCHEMA = schema();

	private final RestClient restClient;
	private final String apiKey;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final String promptTemplate;

	OpenAiLlmClient(RestClient restClient, String apiKey, JsonMapper jsonMapper, Clock clock) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.promptTemplate = loadPrompt();
	}

	@Override
	public VoiceChatResponse chat(VoiceContext context, List<VoiceMessage> messages) {
		if (apiKey == null || apiKey.isBlank()) {
			throw unavailable("OPENAI_API_KEY가 없어 OpenAI를 부르지 않았습니다", null);
		}
		ResponsesResponse response;
		try {
			response = restClient.post()
				.uri(URI.create(RESPONSES_URL))
				.contentType(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(apiKey))
				.body(request(context, messages))
				.retrieve()
				.body(ResponsesResponse.class);
		}
		catch (RestClientException e) {
			throw unavailable("OpenAI 호출에 실패했습니다", e);
		}
		logUsage(response);
		return toResponse(outputText(response));
	}

	private ResponsesRequest request(VoiceContext context, List<VoiceMessage> messages) {
		List<InputMessage> input = messages.stream()
			.map(message -> new InputMessage(message.speaker() == VoiceSpeaker.CUSTOMER ? "user" : "assistant",
					message.text()))
			.toList();
		return new ResponsesRequest(MODEL, instructions(context), input, new Reasoning(REASONING_EFFORT),
				MAX_OUTPUT_TOKENS, false, new Text(new Format("json_schema", "voice_chat", true, SCHEMA)));
	}

	/**
	 * "다음 달 초" 같은 말을 풀 수 있게 오늘 날짜와 요일을 넣고, 상품 목록과 지금 고른 내용을 JSON으로 넣는다.
	 * 오늘 날짜를 먼저 넣는다 — 상품 목록의 글자(옵션 이름 등)에 든 자리 표시가 바뀌지 않게.
	 */
	private String instructions(VoiceContext context) {
		LocalDate today = LocalDate.now(clock);
		String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
		return promptTemplate.replace(TODAY_PLACEHOLDER, today + "(" + weekday + ")")
			.replace(CONTEXT_PLACEHOLDER, jsonMapper.writeValueAsString(context));
	}

	/** 프롬프트 길이와 응답 한도를 다시 정할 때 보려고 쓴 토큰 수를 남긴다. */
	private static void logUsage(ResponsesResponse response) {
		if (response != null && response.usage() != null) {
			Usage usage = response.usage();
			Integer reasoning = usage.outputTokensDetails() == null ? null : usage.outputTokensDetails().reasoningTokens();
			log.info("OpenAI 음성 대화: 입력 {}토큰, 출력 {}토큰(그중 추론 {}토큰)", usage.inputTokens(), usage.outputTokens(),
					reasoning);
		}
	}

	/** 완료된 응답의 message에서 답 글자를 꺼낸다. 잘렸거나(incomplete) 거절(refusal)했으면 해석하지 않는다. */
	private static String outputText(ResponsesResponse response) {
		if (response == null) {
			throw unavailable("OpenAI 응답 본문이 비어 있습니다", null);
		}
		if (!"completed".equals(response.status())) {
			String reason = response.incompleteDetails() == null ? null : response.incompleteDetails().reason();
			throw unavailable("OpenAI 응답이 완료되지 않았습니다: status=" + response.status() + ", reason=" + reason, null);
		}
		StringBuilder text = new StringBuilder();
		for (OutputItem item : nullToEmpty(response.output())) {
			if (!"message".equals(item.type())) {
				continue;   // 추론(reasoning) 항목 등
			}
			for (ContentPart part : nullToEmpty(item.content())) {
				if ("refusal".equals(part.type())) {
					throw unavailable("OpenAI가 답을 거절했습니다: " + part.refusal(), null);
				}
				if ("output_text".equals(part.type()) && part.text() != null) {
					text.append(part.text());
				}
			}
		}
		if (text.isEmpty()) {
			throw unavailable("OpenAI 응답에 답 글자가 없습니다", null);
		}
		return text.toString();
	}

	/**
	 * 답 JSON을 읽는다. 할 말이 비었거나 다음에 할 일을 읽을 수 없으면 503이다 — 빈 할 말은 다음 요청에서 빈 시스템 줄(400)이 된다.
	 * 스키마를 벗어난 테마·등급은 고르지 않은 것으로 비운다. 상품·등급·인원·옵션이 고를 수 있는 값인지는 VoiceService가 본다.
	 */
	private VoiceChatResponse toResponse(String text) {
		Answer answer;
		try {
			answer = jsonMapper.readValue(text, Answer.class);
		}
		catch (JacksonException e) {
			throw unavailable("OpenAI 답을 JSON으로 읽지 못했습니다: " + text, e);
		}
		if (answer == null) {
			throw unavailable("OpenAI 답이 비어 있습니다: " + text, null);
		}
		String reply = TextInput.normalize(answer.reply());
		if (reply == null || reply.isEmpty()) {
			throw unavailable("OpenAI 답에 할 말이 없습니다: " + text, null);
		}
		VoiceNext next = enumOrNull(VoiceNext.class, answer.next());
		if (next == null) {
			throw unavailable("OpenAI 답의 다음에 할 일을 읽지 못했습니다: " + text, null);
		}
		Theme theme = enumOrNull(Theme.class, answer.theme());
		TourLevel level = enumOrNull(TourLevel.class, answer.level());
		if (theme == null && answer.theme() != null || level == null && answer.level() != null) {
			log.warn("OpenAI 답에서 쓸 수 없는 테마·등급을 비웠습니다: {}", text);
		}
		return new VoiceChatResponse(reply,
				new VoiceDraft(theme, answer.tourId(), level, answer.headcount(), answer.optionIds()), next);
	}

	private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
		if (name == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	/** 화면이 손으로 고르라고 안내할 수 있게 503으로 돌려준다. 자세한 이유는 서버 로그에만 남는다(GlobalExceptionHandler). */
	private static ResponseStatusException unavailable(String reason, Throwable cause) {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
	}

	private static String loadPrompt() {
		try (InputStream in = OpenAiLlmClient.class.getResourceAsStream(PROMPT_FILE)) {
			if (in == null) {
				throw new IllegalStateException("프롬프트 파일이 없습니다: " + PROMPT_FILE);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("프롬프트 파일을 읽지 못했습니다: " + PROMPT_FILE, e);
		}
	}

	/**
	 * 답 JSON 스키마. strict 구조화 출력은 모든 항목을 required로 두고 null을 허용해 "고르지 않음"을 나타낸다.
	 * 테마·등급·다음에 할 일의 값은 열거형에서 만든다 — 열거형이 바뀌면 스키마도 따라 바뀐다(프롬프트의 설명은 손으로 고친다).
	 */
	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("reply", property("string", "고객에게 할 말"));
		properties.put("theme", nullableEnum(Theme.values(), "테마"));
		properties.put("tourId", nullable("integer", "상품 ID(tours의 tourId)"));
		properties.put("level", nullableEnum(TourLevel.values(), "투어 등급"));
		properties.put("headcount", nullable("integer", "인원(명)"));
		Map<String, Object> optionIds = property("array", "고른 옵션의 ID(그 상품의 options의 optionId)");
		optionIds.put("items", Map.of("type", "integer"));
		properties.put("optionIds", optionIds);
		Map<String, Object> next = property("string", "다음에 할 일");
		next.put("enum", Arrays.stream(VoiceNext.values()).map(Enum::name).toList());
		properties.put("next", next);
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.copyOf(properties.keySet()));
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> property(String type, String description) {
		Map<String, Object> property = new LinkedHashMap<>();
		property.put("type", type);
		property.put("description", description);
		return property;
	}

	private static Map<String, Object> nullable(String type, String description) {
		Map<String, Object> property = new LinkedHashMap<>();
		property.put("type", List.of(type, "null"));
		property.put("description", description);
		return property;
	}

	private static Map<String, Object> nullableEnum(Enum<?>[] values, String description) {
		List<String> names = new ArrayList<>(Arrays.stream(values).map(Enum::name).toList());
		names.add(null);
		Map<String, Object> property = nullable("string", description);
		property.put("enum", names);
		return property;
	}

	record ResponsesRequest(String model, String instructions, List<InputMessage> input, Reasoning reasoning,
			@JsonProperty("max_output_tokens") int maxOutputTokens, boolean store, Text text) {
	}

	/** 대화 한 줄. role은 고객이면 user, 시스템이면 assistant다. */
	record InputMessage(String role, String content) {
	}

	record Reasoning(String effort) {
	}

	record Text(Format format) {
	}

	record Format(String type, String name, boolean strict, Map<String, Object> schema) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponsesResponse(String status, @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
			List<OutputItem> output, Usage usage) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record IncompleteDetails(String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OutputItem(String type, List<ContentPart> content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ContentPart(String type, String text, String refusal) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Usage(@JsonProperty("input_tokens") Integer inputTokens, @JsonProperty("output_tokens") Integer outputTokens,
			@JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OutputTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Answer(String reply, String theme, Long tourId, String level, Integer headcount, List<Long> optionIds,
			String next) {
	}

}
