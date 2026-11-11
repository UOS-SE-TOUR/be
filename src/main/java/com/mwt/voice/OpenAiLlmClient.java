package com.mwt.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI API(Responses API, POST /v1/responses)를 구조화 출력(JSON 스키마)으로 불러 테마, 출발일, 투어 등급, 인원을 뽑는다
 * (설계산출물 4.7, 5.5 responses.create(prompt, schema)). 프롬프트 원본은 이 클래스 옆의 {@value #PROMPT_FILE} 하나다.
 */
@Slf4j
class OpenAiLlmClient implements LlmClient {

	static final String RESPONSES_URL = "https://api.openai.com/v1/responses";

	/** 설계산출물 4.7 MODEL. */
	static final String MODEL = "gpt-6-luna";

	static final String PROMPT_FILE = "extract-selection-prompt.txt";

	static final String TODAY_PLACEHOLDER = "{{today}}";

	/** 짧은 문장에서 날짜 계산("다음 주 토요일") 정도만 하면 되므로 추론을 낮게 둔다. */
	private static final String REASONING_EFFORT = "low";

	/** 추론 토큰까지 센 응답 한도. 답 JSON은 100토큰이 안 된다. */
	private static final int MAX_OUTPUT_TOKENS = 2000;

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
	public TourSelection extractSelection(String text) {
		if (apiKey == null || apiKey.isBlank()) {
			throw unavailable("OPENAI_API_KEY가 없어 OpenAI를 부르지 않았습니다", null);
		}
		ResponsesResponse response;
		try {
			response = restClient.post()
				.uri(URI.create(RESPONSES_URL))
				.contentType(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(apiKey))
				.body(request(text))
				.retrieve()
				.body(ResponsesResponse.class);
		}
		catch (RestClientException e) {
			throw unavailable("OpenAI 호출에 실패했습니다", e);
		}
		return toSelection(outputText(response));
	}

	private ResponsesRequest request(String text) {
		return new ResponsesRequest(MODEL, instructions(), text.strip(), new Reasoning(REASONING_EFFORT), MAX_OUTPUT_TOKENS,
				false, new Text(new Format("json_schema", "tour_selection", true, SCHEMA)));
	}

	/** "다음 주 토요일" 같은 말을 풀 수 있게 오늘 날짜와 요일을 넣는다. */
	private String instructions() {
		LocalDate today = LocalDate.now(clock);
		String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
		return promptTemplate.replace(TODAY_PLACEHOLDER, today + "(" + weekday + ")");
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

	/** 스키마를 벗어난 값(없는 테마, 없는 날짜, 1보다 작은 인원)은 뽑지 못한 것으로 보고 비운다. */
	private TourSelection toSelection(String text) {
		Extracted extracted;
		try {
			extracted = jsonMapper.readValue(text, Extracted.class);
		}
		catch (JacksonException e) {
			throw unavailable("OpenAI 답을 JSON으로 읽지 못했습니다: " + text, e);
		}
		if (extracted == null) {
			throw unavailable("OpenAI 답이 비어 있습니다: " + text, null);
		}
		Theme theme = enumOrNull(Theme.class, extracted.theme());
		LocalDate departureDate = dateOrNull(extracted.departureDate());
		TourLevel level = enumOrNull(TourLevel.class, extracted.level());
		Integer headcount = extracted.headcount() != null && extracted.headcount() >= 1 ? extracted.headcount() : null;
		if (theme == null && extracted.theme() != null || departureDate == null && extracted.departureDate() != null
				|| level == null && extracted.level() != null || headcount == null && extracted.headcount() != null) {
			log.warn("OpenAI 답에서 쓸 수 없는 값을 비웠습니다: {}", text);
		}
		return TourSelection.extracted(theme, departureDate, level, headcount);
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

	private static LocalDate dateOrNull(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
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
	 * 답 JSON 스키마. strict 구조화 출력은 모든 항목을 required로 두고 null을 허용해 "뽑지 못함"을 나타낸다.
	 * 테마와 등급의 값은 열거형에서 만든다 — 열거형이 바뀌면 스키마도 따라 바뀐다(프롬프트의 설명은 손으로 고친다).
	 */
	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("theme", nullableEnum(Theme.values(), "여행 테마"));
		properties.put("departureDate", nullable("string", "출발일(YYYY-MM-DD)"));
		properties.put("level", nullableEnum(TourLevel.values(), "투어 등급"));
		properties.put("headcount", nullable("integer", "여행할 사람 수(1 이상)"));
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.copyOf(properties.keySet()));
		schema.put("additionalProperties", false);
		return schema;
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

	record ResponsesRequest(String model, String instructions, String input, Reasoning reasoning,
			@JsonProperty("max_output_tokens") int maxOutputTokens, boolean store, Text text) {
	}

	record Reasoning(String effort) {
	}

	record Text(Format format) {
	}

	record Format(String type, String name, boolean strict, Map<String, Object> schema) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponsesResponse(String status, @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
			List<OutputItem> output) {
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
	record Extracted(String theme, String departureDate, String level, Integer headcount) {
	}

}
