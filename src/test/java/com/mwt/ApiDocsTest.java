package com.mwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
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

	/** 열거형에 값을 더하고 @Schema 설명에 뜻을 빠뜨리거나, 화면 이름을 바꾸고 설명을 안 고치면 걸린다. */
	@Test
	void 열거값의_뜻이_모두_있고_테마와_투어_등급은_화면_이름과_같다() throws Exception {
		String docs = apiDocs();
		for (Class<? extends Enum<?>> type : List.<Class<? extends Enum<?>>>of(Theme.class, TourLevel.class,
				TourStatus.class, MemberType.class, OptionCategory.class)) {
			String description = JsonPath.read(docs, "$.components.schemas." + type.getSimpleName() + ".description");
			for (Enum<?> value : type.getEnumConstants()) {
				assertThat(description).as(type.getSimpleName()).contains(value.name() + "(");
			}
		}
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
				.value("#/components/schemas/TourOption"))
			.andExpect(jsonPath("$.components.schemas.TourOption.properties.category").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.QuoteSummary.properties.options.items['$ref']")
				.value("#/components/schemas/SelectedOption"))
			.andExpect(jsonPath("$.components.schemas.SelectedOption.properties.category").exists())
			.andExpect(jsonPath("$.components.schemas.Option").doesNotExist());
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

	private String apiDocs() throws Exception {
		return mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
