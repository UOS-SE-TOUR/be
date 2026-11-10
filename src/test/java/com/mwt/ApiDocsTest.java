package com.mwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.mwt.common.security.JwtTokenProvider;
import com.mwt.member.MemberType;
import com.mwt.tour.OptionCategory;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourStatus;

import jakarta.servlet.http.Cookie;

/**
 * 서버 전체를 띄워 API 문서(Swagger) 주소와, 없는 API 주소의 오류 응답을 본다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTest {

	private static final Pattern SUMMARY = Pattern.compile(".+ \\((누구나|로그인한 회원|고객|직원)\\)");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Test
	void Swagger_UI_주소는_화면으로_넘긴다() throws Exception {
		mockMvc.perform(get("/api/swagger-ui.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/api/swagger-ui/index.html"));
		mockMvc.perform(get("/api/swagger-ui/index.html"))
			.andExpect(status().isOk());
	}

	@Test
	void API_문서를_JSON으로_준다() throws Exception {
		mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("MWT Tour Server API"))
			.andExpect(jsonPath("$.servers[0].url").value("/"));
		mockMvc.perform(get("/api/docs.yaml"))
			.andExpect(status().isOk());
	}

	@Test
	void 로그인한_회원_ID는_API_문서의_요청_값에_나오지_않는다() throws Exception {
		mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/bookings/previous'].get").exists())
			.andExpect(jsonPath("$.paths['/api/v1/bookings/previous'].get.parameters").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/v1/bookings/quote'].post.requestBody").exists())
			.andExpect(jsonPath("$.paths['/api/v1/bookings/quote'].post.parameters").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/v1/bookings'].post.requestBody").exists())
			.andExpect(jsonPath("$.paths['/api/v1/bookings'].post.parameters").doesNotExist());
	}

	/**
	 * 새 API에 @Operation(summary)이나 끝 괄호(부를 수 있는 사람)를 빠뜨리거나, 로그인이 필요한 API에 401·403 설명을
	 * 빠뜨리면 여기서 걸린다(CLAUDE.md 코딩 규칙). 괄호의 말은 README "로그인과 권한" 표의 말이다.
	 */
	@Test
	void 모든_API에_한_줄_설명과_부를_수_있는_사람이_있다() throws Exception {
		Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(apiDocs(), "$.paths");
		List<String> wrong = new ArrayList<>();
		paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
			String where = method.toUpperCase(Locale.ROOT) + " " + path;
			Matcher summary = SUMMARY.matcher(operation.get("summary") instanceof String s ? s : "");
			if (!summary.matches()) {
				wrong.add(where + " — summary가 \"<한 줄 설명> (누구나|로그인한 회원|고객|직원)\" 꼴이 아님");
				return;
			}
			@SuppressWarnings("unchecked")
			Set<String> codes = ((Map<String, Object>) operation.get("responses")).keySet();
			List<String> needed = switch (summary.group(1)) {
				case "로그인한 회원" -> List.of("401");
				case "고객", "직원" -> List.of("401", "403");
				default -> List.of();
			};
			needed.stream().filter(code -> !codes.contains(code))
				.forEach(code -> wrong.add(where + " — " + code + " 실패 응답 설명이 없음"));
		}));
		assertThat(paths).isNotEmpty();
		assertThat(wrong).isEmpty();
	}

	/**
	 * 한 줄 설명 끝 괄호의 말이 실제 권한 규칙(common.security의 SecurityConfig)과 같은지 API마다 불러 본다 —
	 * 로그인 없이 부르면 401인지, 고객·직원 계정으로 부르면 403인지. 본문은 빈 JSON이라 검증(400)에서 끝나 데이터를 바꾸지 않는다.
	 */
	@Test
	void 끝_괄호의_부를_수_있는_사람은_실제_권한_규칙과_같다() throws Exception {
		Cookie customer = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "CUSTOMER"));
		Cookie staff = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "STAFF"));
		Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(apiDocs(), "$.paths");
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<String, Map<String, Map<String, Object>>> path : paths.entrySet()) {
			for (Map.Entry<String, Map<String, Object>> operation : path.getValue().entrySet()) {
				Matcher summary = SUMMARY.matcher(String.valueOf(operation.getValue().get("summary")));
				if (!summary.matches()) {
					continue;   // 꼴이 틀린 summary는 모든_API에_한_줄_설명과_부를_수_있는_사람이_있다가 잡는다
				}
				String caller = summary.group(1);
				HttpMethod method = HttpMethod.valueOf(operation.getKey().toUpperCase(Locale.ROOT));
				String url = path.getKey().replaceAll("\\{[^}]+}", "1");
				String where = method + " " + path.getKey() + " (" + caller + ")";
				if ((statusOf(method, url, null) == 401) == caller.equals("누구나")) {
					wrong.add(where + " — 로그인 없이 부른 결과가 괄호와 다름");
				}
				if ((statusOf(method, url, customer) == 403) != caller.equals("직원")) {
					wrong.add(where + " — 고객 계정으로 부른 결과가 괄호와 다름");
				}
				if ((statusOf(method, url, staff) == 403) != caller.equals("고객")) {
					wrong.add(where + " — 직원 계정으로 부른 결과가 괄호와 다름");
				}
				checked++;
			}
		}
		assertThat(checked).isPositive();
		assertThat(wrong).isEmpty();
	}

	/** 열거형에 값을 더하고 @Schema 설명에 뜻을 빠뜨리거나, 화면 이름을 바꾸고 설명을 안 고치면 걸린다. */
	@Test
	void 열거값의_뜻이_모두_있고_테마와_투어_등급은_화면_이름과_같다() throws Exception {
		String docs = apiDocs();
		Map<String, Map<String, Object>> schemas = JsonPath.read(docs, "$.components.schemas");
		List<String> missing = new ArrayList<>();
		schemas.forEach((name, schema) -> {
			if (schema.get("enum") instanceof List<?> values) {
				String description = schema.get("description") instanceof String d ? d : "";
				// 값 이름 앞이 경계여야 한다 — 아니면 PROGRESS가 IN_PROGRESS(에 걸려 뜻이 있는 것처럼 보인다
				values.stream().map(String::valueOf)
					.filter(value -> !Pattern.compile("(^|[\\s,(])" + Pattern.quote(value) + "\\(").matcher(description).find())
					.forEach(value -> missing.add(name + "." + value));
			}
		});
		assertThat(schemas).containsKeys(Theme.class.getSimpleName(), TourLevel.class.getSimpleName(),
				TourStatus.class.getSimpleName(), MemberType.class.getSimpleName(), OptionCategory.class.getSimpleName());
		assertThat(missing).as("@Schema 설명에 뜻이 없는 열거값").isEmpty();
		String theme = JsonPath.read(docs, "$.components.schemas.Theme.description");
		for (Theme value : Theme.values()) {
			assertThat(theme).contains(value.name() + "(" + value.getDisplayName() + ")");
		}
		String level = JsonPath.read(docs, "$.components.schemas.TourLevel.description");
		for (TourLevel value : TourLevel.values()) {
			assertThat(level).contains(value.name() + "(" + value.getDisplayName() + ")");
		}
	}

	@Test
	void 실패_응답의_본문은_모두_ErrorResponse다() throws Exception {
		String docs = apiDocs();
		Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(docs, "$.paths");
		List<String> errorResponses = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
			responses.keySet().stream().filter(code -> code.startsWith("4") || code.startsWith("5")).forEach(code -> {
				String where = method.toUpperCase(Locale.ROOT) + " " + path + " " + code;
				errorResponses.add(where);
				List<String> refs = JsonPath.read(docs, "$.paths['" + path + "']." + method + ".responses['" + code
						+ "'].content.*.schema['$ref']");
				if (!refs.equals(List.of("#/components/schemas/ErrorResponse"))) {
					wrong.add(where + " " + refs);
				}
			});
		}));
		assertThat(errorResponses).isNotEmpty();
		assertThat(wrong).as("본문이 ErrorResponse 하나가 아닌 실패 응답").isEmpty();
		assertThat(JsonPath.<String>read(docs, "$.components.schemas.ErrorResponse.type")).isEqualTo("object");
		assertThat(JsonPath.<String>read(docs, "$.components.schemas.ErrorResponse.properties.message.type"))
			.isEqualTo("string");
	}

	@Test
	void 로그아웃의_성공_응답은_204_하나다() throws Exception {
		Map<String, Object> responses = JsonPath.read(apiDocs(), "$.paths['/api/v1/members/logout'].post.responses");
		assertThat(responses).containsOnlyKeys("204");
	}

	/** 이름이 같은 중첩 record(TourOptionsResponse.Option·PriceQuote.Option)가 문서에서 한 스키마로 합쳐지지 않는다. */
	@Test
	void 옵션_목록과_가격_계산의_옵션_스키마는_따로다() throws Exception {
		mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.schemas.TourOptionsResponse.properties.hotels.items['$ref']")
				.value("#/components/schemas/TourOptionItem"))
			.andExpect(jsonPath("$.components.schemas.TourOptionItem.properties.category").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.QuoteSummary.properties.options.items['$ref']")
				.value("#/components/schemas/SelectedOption"))
			.andExpect(jsonPath("$.components.schemas.SelectedOption.properties.category").exists())
			.andExpect(jsonPath("$.components.schemas.Option").doesNotExist());
	}

	/** 음성으로 고르기에서 알아듣지 못한 조건은 null이 온다. 열거형 칸은 anyOf [열거형, null]이다(OpenApiConfig). */
	@Test
	void 음성으로_고르기의_조건은_null일_수_있다고_적는다() throws Exception {
		String docs = apiDocs();
		String properties = "$.components.schemas.TourSelection.properties.";
		assertThat(JsonPath.<List<String>>read(docs, properties + "theme.anyOf[*]['$ref']"))
			.containsExactly("#/components/schemas/Theme");
		assertThat(JsonPath.<List<Object>>read(docs, properties + "theme.anyOf[*].type")).containsExactly("null");
		assertThat(JsonPath.<List<String>>read(docs, properties + "level.anyOf[*]['$ref']"))
			.containsExactly("#/components/schemas/TourLevel");
		assertThat(JsonPath.<List<Object>>read(docs, properties + "level.anyOf[*].type")).containsExactly("null");
		assertThat(JsonPath.<List<String>>read(docs, properties + "departureDate.type")).contains("string", "null");
		assertThat(JsonPath.<List<String>>read(docs, properties + "headcount.type")).contains("integer", "null");
	}

	@Test
	void 비밀번호의_최소_길이는_서버_규칙과_같다() throws Exception {
		mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.schemas.SignUpRequest.properties.password.minLength").value(8));
	}

	@Test
	void 권한_규칙을_통과한_없는_API_주소는_404와_문구() throws Exception {
		Cookie customer = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "CUSTOMER"));
		mockMvc.perform(get("/api/v1/tours/nothing/here").cookie(customer))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"요청한 주소를 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 권한_규칙이_없는_API_주소는_로그인_전에_401() throws Exception {
		mockMvc.perform(get("/api/v1/nothing"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"message\": \"로그인이 필요합니다.\"}", JsonCompareMode.STRICT));
	}

	private int statusOf(HttpMethod method, String url, Cookie cookie) throws Exception {
		MockHttpServletRequestBuilder request = request(method, url).contentType(MediaType.APPLICATION_JSON).content("{}");
		if (cookie != null) {
			request.cookie(cookie);
		}
		return mockMvc.perform(request).andReturn().getResponse().getStatus();
	}

	private String apiDocs() throws Exception {
		return mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
