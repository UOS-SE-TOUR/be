package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.mwt.TestcontainersConfiguration;

/**
 * 여행 상품 기획 API(POST /api/v1/staff/tours, 설계산출물 5.8). 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다.
 * "오늘"은 2026-11-10(서울)로 고정한다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StaffTourControllerTest {

	private static final String URL = "/api/v1/staff/tours";
	private static final String PRICES = """
			[{"level": "GRAND", "price": 1500000}, {"level": "PREMIUM", "price": 2500000}]""";
	private static final String TRANSPORTS = """
			[{"name": "비행기 이코노미석", "unitPrice": 0}, {"name": "비행기 비즈니스석", "unitPrice": 300000}]""";

	@TestBean
	Clock clock;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TourRepository tourRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	}

	@AfterEach
	void deleteTours() {
		tourRepository.deleteAll();
	}

	@Test
	void 상품을_등록하면_201과_상품_ID를_돌려주고_가격과_옵션을_저장한다() throws Exception {
		String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content(body("HONEYMOON", "2026-12-01", "5", "4", PRICES, TRANSPORTS)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tourId").isNumber())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long tourId = JsonPath.<Number>read(response, "$.tourId").longValue();

		transactionTemplate.executeWithoutResult(transaction -> {
			Tour tour = tourRepository.findById(tourId).orElseThrow();
			assertThat(tour.getTheme()).isEqualTo(Theme.HONEYMOON);
			assertThat(tour.getDepartureDate()).hasToString("2026-12-01");
			assertThat(tour.getDuration()).isEqualTo(5);
			assertThat(tour.getCapacity()).isEqualTo(4);
			assertThat(tour.getAppliedCount()).isZero();
			assertThat(tour.getStatus()).isEqualTo(TourStatus.RECRUITING);
			assertThat(tour.getPrices()).extracting(TourPrice::getLevel, TourPrice::getPrice)
				.containsExactlyInAnyOrder(tuple(TourLevel.GRAND, 1_500_000L), tuple(TourLevel.PREMIUM, 2_500_000L));
			assertThat(tour.getOptions()).extracting(TourOption::getName)
				.containsExactlyInAnyOrder("비행기 이코노미석", "비행기 비즈니스석", "4등급 호텔", "5등급 호텔",
						"도시락", "현지식 레스토랑", "고급 레스토랑", "샴페인", "커피");
			assertThat(tour.getOptions()).allSatisfy(option -> assertThat(option.getId()).isNotNull());
		});
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("rejectedRequests")
	void 규칙에_어긋나면_400과_이유를_돌려주고_저장하지_않는다(String requestBody, String message) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));

		assertThat(tourRepository.count()).isZero();
	}

	static Stream<Arguments> rejectedRequests() {
		return Stream.of(
				Arguments.of(body("GOLF", "2026-11-10", "5", "10", golfPrices(), TRANSPORTS), "출발일은 오늘보다 뒤여야 합니다."),
				Arguments.of(body("GOLF", "2026-11-09", "5", "10", golfPrices(), TRANSPORTS), "출발일은 오늘보다 뒤여야 합니다."),
				Arguments.of(body("GOLF", "2026-12-01", "0", "10", golfPrices(), TRANSPORTS), "기간은 1일 이상이어야 합니다."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "2", golfPrices(), TRANSPORTS), "정원은 3명 이상이어야 합니다."),
				Arguments.of(body("HONEYMOON", "2026-12-01", "5", "5", PRICES, TRANSPORTS), "허니문 낭만 투어의 정원은 짝수여야 합니다."),
				Arguments.of(body("PARENTS", "2026-12-01", "5", "10", golfPrices(), TRANSPORTS),
						"효도 힐링 투어는 그랜드와 프리미엄 가격만 입력합니다."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", PRICES, TRANSPORTS), "클래식 가격을 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", golfPrices(), """
						[{"name": "비행기 비즈니스석", "unitPrice": 300000}]"""), "추가 요금이 0원인 기본 교통을 넣어 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", golfPrices(), """
						[{"name": "비행기 이코노미석", "unitPrice": 0}, {"name": "고속버스", "unitPrice": 0}]"""),
						"추가 요금이 0원인 기본 교통은 하나만 넣을 수 있습니다."),
				Arguments.of(body(null, "2026-12-01", "5", "10", golfPrices(), TRANSPORTS), "테마를 골라 주세요."),
				Arguments.of(body("GOLF", null, "5", "10", golfPrices(), TRANSPORTS), "출발일을 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", null, "10", golfPrices(), TRANSPORTS), "기간을 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", null, golfPrices(), TRANSPORTS), "정원을 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", "[]", TRANSPORTS), "등급별 가격을 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", golfPrices(), "[]"), "교통 선택지를 입력해 주세요."),
				Arguments.of(body("GOLF", "2026-12-01", "5", "10", golfPrices(), """
						[{"name": " ", "unitPrice": 0}]"""), "교통 이름을 입력해 주세요."));
	}

	@ParameterizedTest
	@MethodSource("notIntegerRequests")
	void 기간이나_정원이_정수가_아니면_잘라_받지_않고_400(String requestBody) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요.\"}",
					JsonCompareMode.STRICT));

		assertThat(tourRepository.count()).isZero();
	}

	static Stream<String> notIntegerRequests() {
		return Stream.of(
				body("GOLF", "2026-12-01", "2.5", "10", golfPrices(), TRANSPORTS),
				body("GOLF", "2026-12-01", "5", "10.5", golfPrices(), TRANSPORTS));
	}

	private static String golfPrices() {
		return """
				[{"level": "CLASSIC", "price": 900000}, {"level": "GRAND", "price": 1500000}, \
				{"level": "PREMIUM", "price": 2500000}]""";
	}

	/** 빈 항목은 null로 준다. 테마와 출발일은 문자열, 기간과 정원은 숫자 그대로 넣는다. */
	private static String body(String theme, String departureDate, String duration, String capacity, String prices,
			String transports) {
		return """
				{"theme": %s, "departureDate": %s, "duration": %s, "capacity": %s, "prices": %s, "transports": %s}"""
			.formatted(quoted(theme), quoted(departureDate), duration, capacity, prices, transports);
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

}
