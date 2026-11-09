package com.mwt.tour;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.tour.RegisterTourRequest.PriceInput;
import com.mwt.tour.RegisterTourRequest.TransportInput;

/**
 * 고객의 상품 조회 API 셋(설계산출물 4.9 서버 API 표, 5.4·5.6). 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다.
 * "오늘"은 2026-11-10(서울)로 고정한다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TourControllerTest {

	private static final List<PriceInput> ALL_PRICES = List.of(new PriceInput(TourLevel.CLASSIC, 900_000L),
			new PriceInput(TourLevel.GRAND, 1_500_000L), new PriceInput(TourLevel.PREMIUM, 2_500_000L));
	// 등급 순서와 다르게 넣어 응답이 등급 순으로 정렬되는지 본다
	private static final List<PriceInput> GRAND_AND_PREMIUM_PRICES = List.of(new PriceInput(TourLevel.PREMIUM, 2_500_000L),
			new PriceInput(TourLevel.GRAND, 1_500_000L));
	private static final List<TransportInput> TRANSPORTS = List.of(new TransportInput("비행기 이코노미석", 0L),
			new TransportInput("비행기 비즈니스석", 300_000L));

	@TestBean
	Clock clock;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TourService tourService;

	@Autowired
	TourRepository tourRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	}

	@AfterEach
	void deleteTours() {
		tourRepository.deleteAll();
	}

	@Test
	void 상품_목록은_출발일이_오늘보다_뒤인_그_테마의_상품을_마감까지_출발일_순으로_돌려준다() throws Exception {
		long later = register(Theme.GOLF, "2026-12-01", 3, 10, ALL_PRICES);
		long full = register(Theme.GOLF, "2026-11-20", 5, 10, ALL_PRICES);
		jdbcTemplate.update("update tour set applied_count = capacity, status = 'FULL' where id = ?", full);
		register(Theme.TREKKING, "2026-11-20", 5, 10, ALL_PRICES);
		saveDeparted(Theme.GOLF, "2026-11-10");
		saveDeparted(Theme.GOLF, "2026-11-01");

		mockMvc.perform(get("/api/v1/tours").param("theme", "GOLF"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					[{"tourId": %d, "title": "골프 챌린지 투어 11/20 출발 5일", "departureDate": "2026-11-20", "duration": 5,
					  "remainingSeats": 0, "status": "FULL"},
					 {"tourId": %d, "title": "골프 챌린지 투어 12/1 출발 3일", "departureDate": "2026-12-01", "duration": 3,
					  "remainingSeats": 10, "status": "RECRUITING"}]""".formatted(full, later), JsonCompareMode.STRICT));
	}

	@Test
	void 그_테마의_다가오는_상품이_없으면_빈_목록() throws Exception {
		saveDeparted(Theme.PARENTS, "2026-11-10");

		mockMvc.perform(get("/api/v1/tours").param("theme", "PARENTS"))
			.andExpect(status().isOk())
			.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

	@Test
	void 상품_목록에_테마가_없으면_400() throws Exception {
		mockMvc.perform(get("/api/v1/tours"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"필수 요청 값이 없습니다: theme\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 상품_목록의_테마가_없는_값이면_400() throws Exception {
		mockMvc.perform(get("/api/v1/tours").param("theme", "golf"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"요청 값의 형식이 올바르지 않습니다: theme\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 투어_등급은_고를_수_있는_등급만_등급_순으로_가격과_제공_내용을_돌려준다() throws Exception {
		long tourId = register(Theme.HONEYMOON, "2026-12-01", 5, 4, GRAND_AND_PREMIUM_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/levels", tourId))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"service": "로맨틱 스페셜 룸 데코레이션(2인 전용)", "souvenir": "커플 기념티", "vehicle": "2인 전용 고급 차량",
					 "levels": [
					   {"level": "GRAND", "name": "그랜드", "price": 1500000, "hotelGrade": 4, "meal": "현지식 레스토랑",
					    "includesChampagne": false},
					   {"level": "PREMIUM", "name": "프리미엄", "price": 2500000, "hotelGrade": 5, "meal": "고급 레스토랑",
					    "includesChampagne": true}]}""", JsonCompareMode.STRICT));
	}

	@Test
	void 클래식을_고를_수_있는_테마면_클래식이_먼저다() throws Exception {
		long tourId = register(Theme.GOLF, "2026-12-01", 5, 10, ALL_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/levels", tourId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.levels[*].level").value(contains("CLASSIC", "GRAND", "PREMIUM")))
			.andExpect(jsonPath("$.levels[0].price").value(900_000))
			.andExpect(jsonPath("$.levels[0].hotelGrade").value(3))
			.andExpect(jsonPath("$.levels[0].meal").value("도시락"));
	}

	@Test
	void 없는_상품의_투어_등급은_404() throws Exception {
		mockMvc.perform(get("/api/v1/tours/{tourId}/levels", Long.MAX_VALUE))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"여행 상품을 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 옵션은_종류별로_받은_투어_등급_기준의_1인_요금_차이와_함께_돌려준다() throws Exception {
		long tourId = register(Theme.GOLF, "2026-12-01", 5, 10, ALL_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/options", tourId).param("level", "GRAND"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hotels[*].name").value(contains("3등급 호텔", "4등급 호텔", "5등급 호텔")))
			.andExpect(jsonPath("$.hotels[*].priceDiff").value(contains(-50_000, 0, 100_000)))
			.andExpect(jsonPath("$.transports[*].name").value(contains("비행기 이코노미석", "비행기 비즈니스석")))
			.andExpect(jsonPath("$.transports[*].priceDiff").value(contains(0, 300_000)))
			.andExpect(jsonPath("$.meals[*].name").value(contains("도시락", "현지식 레스토랑", "고급 레스토랑")))
			.andExpect(jsonPath("$.meals[*].priceDiff").value(contains(-20_000, 0, 50_000)))
			.andExpect(jsonPath("$.extras[*].name").value(contains("샴페인", "커피")))
			.andExpect(jsonPath("$.extras[*].priceDiff").value(contains(50_000, 5_000)))
			.andExpect(jsonPath("$..optionId").value(everyItem(isA(Number.class))));
	}

	@Test
	void 프리미엄_기준이면_샴페인은_기본_제공이라_0원이다() throws Exception {
		long tourId = register(Theme.GOLF, "2026-12-01", 5, 10, ALL_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/options", tourId).param("level", "PREMIUM"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hotels[*].priceDiff").value(contains(-150_000, -100_000, 0)))
			.andExpect(jsonPath("$.meals[*].priceDiff").value(contains(-70_000, -50_000, 0)))
			.andExpect(jsonPath("$.extras[*].priceDiff").value(contains(0, 5_000)));
	}

	@Test
	void 허니문_상품에는_3등급_호텔과_도시락이_없다() throws Exception {
		long tourId = register(Theme.HONEYMOON, "2026-12-01", 5, 4, GRAND_AND_PREMIUM_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/options", tourId).param("level", "GRAND"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hotels[*].name").value(contains("4등급 호텔", "5등급 호텔")))
			.andExpect(jsonPath("$.meals[*].name").value(contains("현지식 레스토랑", "고급 레스토랑")));
	}

	@Test
	void 테마가_고를_수_없는_투어_등급이면_400() throws Exception {
		long tourId = register(Theme.HONEYMOON, "2026-12-01", 5, 4, GRAND_AND_PREMIUM_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/options", tourId).param("level", "CLASSIC"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"허니문 낭만 투어는 그랜드와 프리미엄만 고를 수 있습니다.\"}",
					JsonCompareMode.STRICT));
	}

	@Test
	void 옵션에_투어_등급이_없으면_400() throws Exception {
		long tourId = register(Theme.GOLF, "2026-12-01", 5, 10, ALL_PRICES);

		mockMvc.perform(get("/api/v1/tours/{tourId}/options", tourId))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"필수 요청 값이 없습니다: level\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 없는_상품의_옵션은_404() throws Exception {
		mockMvc.perform(get("/api/v1/tours/{tourId}/options", Long.MAX_VALUE).param("level", "GRAND"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"여행 상품을 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

	private long register(Theme theme, String departureDate, int duration, int capacity, List<PriceInput> prices) {
		return tourService.registerTour(new RegisterTourRequest(theme, LocalDate.parse(departureDate), duration, capacity,
				prices, TRANSPORTS));
	}

	/** 출발일 하루 전을 "오늘"로 넣어 만든다. 그래서 오늘이거나 지난 출발일의 상품도 저장할 수 있다. */
	private void saveDeparted(Theme theme, String departureDate) {
		LocalDate date = LocalDate.parse(departureDate);
		tourRepository.save(new Tour(theme, date, 5, 10, date.minusDays(1)));
	}

}
