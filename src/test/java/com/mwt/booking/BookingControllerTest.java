package com.mwt.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.booking.RecordingSmsSender.Sent;
import com.mwt.common.security.JwtTokenProvider;
import com.mwt.notification.SmsSender;
import com.mwt.tour.RegisterTourRequest;
import com.mwt.tour.RegisterTourRequest.PriceInput;
import com.mwt.tour.RegisterTourRequest.TransportInput;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourService;

import jakarta.servlet.http.Cookie;

/**
 * 가격 계산(POST /api/v1/bookings/quote), 신청(POST /api/v1/bookings), 이전 여행 조회(GET /api/v1/bookings/previous)
 * API(설계산출물 4.9 서버 API 표, 5.3·5.4·5.4.1·5.6·5.7). 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다.
 * "오늘"은 2026-11-10(서울)로 고정하고, 문자는 보내지 않고 RecordingSmsSender에 기록한다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

	private static final String QUOTE = "/api/v1/bookings/quote";
	private static final String APPLY = "/api/v1/bookings";
	private static final String PREVIOUS = "/api/v1/bookings/previous";
	private static final String CONFIRMED_MESSAGE = "[미스터 월드] 신청하신 여행 상품(골프 챌린지 투어 12/1 출발 5일)이 확정되었습니다.";
	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
	private static final String KEY = "3f2b8c1e-7a4d-4e5f-9b6a-0c1d2e3f4a5b";

	@TestBean
	Clock clock;

	@TestBean
	SmsSender smsSender;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TourService tourService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	}

	static SmsSender smsSender() {
		return new RecordingSmsSender();
	}

	@AfterEach
	void deleteAll() {
		recorder().clear();
		jdbcTemplate.update("delete from booking_option");
		jdbcTemplate.update("delete from booking");
		jdbcTemplate.update("delete from tour_price");
		jdbcTemplate.update("delete from tour_option");
		jdbcTemplate.update("delete from tour");
		jdbcTemplate.update("delete from member");
	}

	@Test
	void 단골이_아니면_할인_없이_고른_내용과_할인_전_총_가격을_돌려준다() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);
		long hotel = optionId(tourId, "5등급 호텔");
		long champagne = optionId(tourId, "샴페인");

		mockMvc.perform(quote(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d, %d]}""".formatted(tourId, champagne, hotel)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"summary": {"tourId": %d, "title": "골프 챌린지 투어 12/1 출발 5일", "departureDate": "2026-12-01",
					             "duration": 5, "level": "GRAND", "levelName": "그랜드", "headcount": 2,
					             "options": [{"optionId": %d, "category": "HOTEL", "name": "5등급 호텔", "priceDiff": 100000},
					                         {"optionId": %d, "category": "EXTRA", "name": "샴페인", "priceDiff": 50000}]},
					 "totalPrice": 3300000, "discount": 0, "finalPrice": 3300000}""".formatted(tourId, hotel, champagne),
					JsonCompareMode.STRICT));
	}

	@Test
	void 옵션을_보내지_않으면_그_등급의_기본_제공으로_계산한다() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);

		mockMvc.perform(quote(me, """
				{"tourId": %d, "level": "PREMIUM", "headcount": 3}""".formatted(tourId)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"summary": {"level": "PREMIUM", "headcount": 3, "options": []},
					 "totalPrice": 7500000, "discount": 0, "finalPrice": 7500000}"""));
	}

	@Test
	void 완료된_이전_여행이_2건_이상이면_10퍼센트_할인한다() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);
		String request = """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": []}""".formatted(tourId);
		booking(me, completedTour("2026-09-01"), 3_000_000, 0);
		booking(me, register(Theme.GOLF), 3_000_000, 0);   // 출발 전 상품의 신청은 이전 여행이 아니다
		booking(member("other"), completedTour("2026-10-01"), 3_000_000, 0);

		mockMvc.perform(quote(me, request))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"totalPrice": 3000000, "discount": 0, "finalPrice": 3000000}"""));

		booking(me, completedTour("2026-10-01"), 3_000_000, 0);

		mockMvc.perform(quote(me, request))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"totalPrice": 3000000, "discount": 300000, "finalPrice": 2700000}"""));
	}

	@Test
	void 규칙에_맞지_않으면_400과_이유() throws Exception {
		long me = member("me");
		long golf = register(Theme.GOLF);
		long honeymoon = register(Theme.HONEYMOON);

		expectRejected(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 3}""".formatted(honeymoon), "허니문 낭만 투어는 2명만 신청할 수 있습니다.");
		expectRejected(me, """
				{"tourId": %d, "level": "CLASSIC", "headcount": 2}""".formatted(honeymoon),
				"허니문 낭만 투어는 그랜드와 프리미엄만 고를 수 있습니다.");
		expectRejected(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 0}""".formatted(golf), "인원은 1명 이상이어야 합니다.");
		expectRejected(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 11}""".formatted(golf), "남은 자리보다 인원이 많습니다.");
		expectRejected(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d]}""".formatted(golf,
				optionId(honeymoon, "샴페인")), "이 상품에 없는 옵션입니다.");
		expectRejected(me, """
				{"tourId": %d, "headcount": 2}""".formatted(golf), "투어 등급을 골라 주세요.");
		expectRejected(me, """
				{"tourId": %d, "level": "GRAND"}""".formatted(golf), "인원을 입력해 주세요.");
		expectRejected(me, """
				{"level": "GRAND", "headcount": 2}""", "여행 상품을 골라 주세요.");
	}

	@Test
	void 없는_상품이면_404() throws Exception {
		mockMvc.perform(quote(member("me"), """
				{"tourId": 999999, "level": "GRAND", "headcount": 2}"""))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"여행 상품을 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 신청하면_201과_상품_상태를_돌려주고_고른_내용과_할인한_가격을_신청_시각과_함께_저장한다() throws Exception {
		long me = member("me");
		booking(me, completedTour("2026-09-01"), 3_000_000, 0);
		booking(me, completedTour("2026-10-01"), 3_000_000, 0);
		long tourId = register(Theme.GOLF);
		long hotel = optionId(tourId, "5등급 호텔");

		ResultActions result = mockMvc.perform(apply(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d]}""".formatted(tourId, hotel)));

		Map<String, Object> saved = jdbcTemplate.queryForMap("""
				select id, member_id, level, headcount, total_price, discount,
				       date_format(applied_at, '%Y-%m-%d %H:%i:%s') as applied_at
				from booking where tour_id = ?""", tourId);
		result.andExpect(status().isCreated())
			.andExpect(content().json("""
					{"bookingId": %d, "status": "RECRUITING"}""".formatted((Long) saved.get("id")), JsonCompareMode.STRICT));
		assertThat(saved).containsAllEntriesOf(Map.of("member_id", me, "level", "GRAND", "headcount", 2,
				"total_price", 3_200_000L, "discount", 320_000L, "applied_at", "2026-11-10 00:00:00"));
		assertThat(jdbcTemplate.queryForList("select tour_option_id from booking_option where booking_id = ?", Long.class,
				saved.get("id"))).containsExactly(hotel);
		assertThat(appliedCount(tourId)).isEqualTo(2);
		assertThat(recorder().sent()).isEmpty();
	}

	@Test
	void 새로_확정되면_그때까지의_신청자_모두에게_이미_확정이면_이번_신청자에게만_문자를_보낸다() throws Exception {
		long first = member("first", "010-1111-2222");
		long second = member("second", "01033334444");
		long third = member("third", "01055556666");
		long tourId = register(Theme.GOLF);

		applyAndExpect(first, tourId, 2, "RECRUITING");
		assertThat(recorder().sent()).isEmpty();

		applyAndExpect(second, tourId, 1, "CONFIRMED");
		assertThat(recorder().sent())
			.containsExactly(new Sent(List.of("010-1111-2222", "01033334444"), CONFIRMED_MESSAGE));

		recorder().clear();
		applyAndExpect(third, tourId, 7, "FULL");
		assertThat(recorder().sent()).containsExactly(new Sent(List.of("01055556666"), CONFIRMED_MESSAGE));
	}

	@Test
	void 남은_자리보다_많은_인원은_저장하지_않고_400() throws Exception {
		long tourId = register(Theme.GOLF);
		applyAndExpect(member("first"), tourId, 8, "CONFIRMED");

		mockMvc.perform(apply(member("second"), request(tourId, 3)))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"남은 자리보다 인원이 많습니다.\"}", JsonCompareMode.STRICT));

		assertThat(bookingCount(tourId)).isEqualTo(1);
		assertThat(appliedCount(tourId)).isEqualTo(8);
	}

	@Test
	void 마감됐거나_출발일이_지난_상품이면_가격_계산도_신청도_409() throws Exception {
		long me = member("me");
		long full = register(Theme.GOLF);
		applyAndExpect(member("other"), full, 10, "FULL");
		long departed = tour("2026-11-10", "RECRUITING", 0);

		expectConflict(QUOTE, me, request(full, 1), "마감된 상품입니다.");
		expectConflict(APPLY, me, request(full, 1), "마감된 상품입니다.");
		expectConflict(QUOTE, me, request(departed, 1), "출발일이 오늘이거나 지난 상품은 신청할 수 없습니다.");
		expectConflict(APPLY, me, request(departed, 1), "출발일이 오늘이거나 지난 상품은 신청할 수 없습니다.");

		assertThat(bookingCount(full)).isEqualTo(1);
		assertThat(bookingCount(departed)).isZero();
	}

	@Test
	void 신청_키를_보내면_신청과_함께_저장하고_같은_신청_키로_다시_보내면_201과_첫_신청의_결과를_돌려준다() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);
		long hotel = optionId(tourId, "5등급 호텔");
		long champagne = optionId(tourId, "샴페인");

		mockMvc.perform(apply(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d, %d]}""".formatted(tourId, hotel, champagne))
				.header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isCreated());
		Map<String, Object> saved = jdbcTemplate.queryForMap("select id, idempotency_key from booking where tour_id = ?",
				tourId);
		assertThat(saved.get("idempotency_key")).isEqualTo(KEY);

		// 옵션은 순서만 다르면 같은 내용이다
		mockMvc.perform(apply(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d, %d]}""".formatted(tourId, champagne, hotel))
				.header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isCreated())
			.andExpect(content().json("""
					{"bookingId": %d, "status": "RECRUITING"}""".formatted((Long) saved.get("id")), JsonCompareMode.STRICT));
		// UUID는 대소문자를 가리지 않으므로 대문자로 보내도 같은 신청 키다
		mockMvc.perform(apply(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d, %d]}""".formatted(tourId, hotel, champagne))
				.header(IDEMPOTENCY_KEY, KEY.toUpperCase(Locale.ROOT)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.bookingId").value(saved.get("id")));
		// 같은 옵션을 두 번 보내면 다른 내용이다(신청 키 없이 보냈으면 거부되는 요청)
		mockMvc.perform(apply(me, """
				{"tourId": %d, "level": "GRAND", "headcount": 2, "optionIds": [%d, %d, %d]}""".formatted(tourId, hotel,
				champagne, hotel)).header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"같은 신청 키로 다른 내용을 신청할 수 없습니다.\"}", JsonCompareMode.STRICT));

		assertThat(bookingCount(tourId)).isEqualTo(1);
		assertThat(appliedCount(tourId)).isEqualTo(2);
	}

	@Test
	void 같은_신청_키로_다른_내용을_보내면_신청하지_않고_400() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);
		mockMvc.perform(apply(me, request(tourId, 2)).header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isCreated());

		mockMvc.perform(apply(me, request(tourId, 3)).header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"같은 신청 키로 다른 내용을 신청할 수 없습니다.\"}", JsonCompareMode.STRICT));

		assertThat(bookingCount(tourId)).isEqualTo(1);
		assertThat(appliedCount(tourId)).isEqualTo(2);
	}

	@Test
	void 신청_키_형식이_틀리면_신청하지_않고_400() throws Exception {
		long me = member("me");
		long tourId = register(Theme.GOLF);
		List<String> malformed = List.of("", "abc", "3f2b8c1e7a4d4e5f9b6a0c1d2e3f4a5b", "{3f2b8c1e-7a4d-4e5f-9b6a-0c1d2e3f4a5b}",
				"3f2b8c1e-7a4d-4e5f-9b6a-0c1d2e3f4a5g", KEY + "0");

		for (String key : malformed) {
			mockMvc.perform(apply(me, request(tourId, 2)).header(IDEMPOTENCY_KEY, key))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\": \"신청 키 형식이 올바르지 않습니다.\"}", JsonCompareMode.STRICT));
		}
		// 신청 키를 검사해도 본문 검증 문구는 그대로다
		mockMvc.perform(apply(me, """
				{"tourId": %d, "headcount": 2}""".formatted(tourId)).header(IDEMPOTENCY_KEY, KEY))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"투어 등급을 골라 주세요.\"}", JsonCompareMode.STRICT));

		assertThat(bookingCount(tourId)).isZero();
		assertThat(appliedCount(tourId)).isZero();
	}

	@Test
	void 이전_여행은_그_회원의_완료된_여행을_출발일이_최근인_순서로_할인_뒤_가격과_함께_돌려준다() throws Exception {
		long me = member("me");
		long older = booking(me, completedTour("2026-09-01"), 3_000_000, 0);
		long newer = booking(me, completedTour("2026-10-20"), 3_300_000, 330_000);
		booking(me, register(Theme.GOLF), 3_000_000, 0);
		booking(member("other"), completedTour("2026-10-01"), 3_000_000, 0);

		mockMvc.perform(customer(get(PREVIOUS), me))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					[{"bookingId": %d, "tourId": %d, "title": "골프 챌린지 투어 10/20 출발 5일", "departureDate": "2026-10-20",
					  "duration": 5, "level": "GRAND", "levelName": "그랜드", "headcount": 2, "finalPrice": 2970000},
					 {"bookingId": %d, "tourId": %d, "title": "골프 챌린지 투어 9/1 출발 5일", "departureDate": "2026-09-01",
					  "duration": 5, "level": "GRAND", "levelName": "그랜드", "headcount": 2, "finalPrice": 3000000}]"""
				.formatted(newer, tourOf(newer), older, tourOf(older)), JsonCompareMode.STRICT));
	}

	@Test
	void 이전_여행이_없으면_빈_목록() throws Exception {
		mockMvc.perform(customer(get(PREVIOUS), member("me")))
			.andExpect(status().isOk())
			.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

	@Test
	void 직원은_쓸_수_없고_로그인하지_않으면_401() throws Exception {
		long tourId = register(Theme.GOLF);
		String request = """
				{"tourId": %d, "level": "GRAND", "headcount": 2}""".formatted(tourId);
		Cookie staff = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "STAFF"));

		mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content(request).cookie(staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(APPLY).contentType(MediaType.APPLICATION_JSON).content(request).cookie(staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(PREVIOUS).cookie(staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(APPLY).contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isUnauthorized());
		assertThat(bookingCount(tourId)).isZero();
	}

	private void expectRejected(long memberId, String request, String message) throws Exception {
		mockMvc.perform(quote(memberId, request))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));
	}

	private void expectConflict(String path, long memberId, String request, String message) throws Exception {
		mockMvc.perform(customer(post(path).contentType(MediaType.APPLICATION_JSON).content(request), memberId))
			.andExpect(status().isConflict())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));
	}

	private void applyAndExpect(long memberId, long tourId, int headcount, String tourStatus) throws Exception {
		mockMvc.perform(apply(memberId, request(tourId, headcount)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(tourStatus));
	}

	private RecordingSmsSender recorder() {
		return (RecordingSmsSender) smsSender;
	}

	/** 그랜드, 옵션 없음. */
	private static String request(long tourId, int headcount) {
		return """
				{"tourId": %d, "level": "GRAND", "headcount": %d}""".formatted(tourId, headcount);
	}

	private MockHttpServletRequestBuilder quote(long memberId, String body) {
		return customer(post(QUOTE).contentType(MediaType.APPLICATION_JSON).content(body), memberId);
	}

	private MockHttpServletRequestBuilder apply(long memberId, String body) {
		return customer(post(APPLY).contentType(MediaType.APPLICATION_JSON).content(body), memberId);
	}

	private MockHttpServletRequestBuilder customer(MockHttpServletRequestBuilder request, long memberId) {
		return request.cookie(new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(memberId, "CUSTOMER")));
	}

	/** 2026-12-01 출발 5일, 정원 10명. 등급별 1인 가격은 클래식 900,000원, 그랜드 1,500,000원, 프리미엄 2,500,000원이다. */
	private long register(Theme theme) {
		List<PriceInput> prices = theme == Theme.HONEYMOON || theme == Theme.PARENTS
				? List.of(new PriceInput(TourLevel.GRAND, 1_500_000L), new PriceInput(TourLevel.PREMIUM, 2_500_000L))
				: List.of(new PriceInput(TourLevel.CLASSIC, 900_000L), new PriceInput(TourLevel.GRAND, 1_500_000L),
						new PriceInput(TourLevel.PREMIUM, 2_500_000L));
		return tourService.registerTour(new RegisterTourRequest(theme, LocalDate.of(2026, 12, 1), 5, 10, prices,
				List.of(new TransportInput("비행기 이코노미석", 0L), new TransportInput("비행기 비즈니스석", 300_000L))));
	}

	/** 출발해 여행 종료일이 지난 골프 상품(5일). 상태 전이(#12)가 아직 없어 행을 직접 넣는다. */
	private long completedTour(String departureDate) {
		return tour(departureDate, "COMPLETED", 2);
	}

	/** 골프 상품(5일, 정원 10명) 행을 직접 넣는다. 등급별 가격과 옵션은 없다 — 출발일·상태 확인에서 거부되는 상품에만 쓴다. */
	private long tour(String departureDate, String status, int appliedCount) {
		return insert("""
				insert into tour (theme, departure_date, duration, capacity, applied_count, status)
				values ('GOLF', ?, 5, 10, ?, ?)""", departureDate, appliedCount, status);
	}

	/** 회원 저장(member 모듈의 #9)이 아직 없어 행을 직접 넣는다. */
	private long member(String loginId) {
		return member(loginId, "01012345678");
	}

	private long member(String loginId, String phone) {
		return insert("""
				insert into member (login_id, password, name, address, phone, member_type)
				values (?, 'encoded', '고객', '서울', ?, 'CUSTOMER')""", loginId, phone);
	}

	/** 그랜드 2명 신청. 완료된 상품에는 신청 API로 신청할 수 없어 행을 직접 넣는다. */
	private long booking(long memberId, long tourId, long totalPrice, long discount) {
		return insert("""
				insert into booking (member_id, tour_id, level, headcount, total_price, discount, applied_at)
				values (?, ?, 'GRAND', 2, ?, ?, '2026-08-01 00:00:00')""", memberId, tourId, totalPrice, discount);
	}

	private long optionId(long tourId, String name) {
		return jdbcTemplate.queryForObject("select id from tour_option where tour_id = ? and name = ?", Long.class, tourId,
				name);
	}

	private long tourOf(long bookingId) {
		return jdbcTemplate.queryForObject("select tour_id from booking where id = ?", Long.class, bookingId);
	}

	private int bookingCount(long tourId) {
		return jdbcTemplate.queryForObject("select count(*) from booking where tour_id = ?", Integer.class, tourId);
	}

	private int appliedCount(long tourId) {
		return jdbcTemplate.queryForObject("select applied_count from tour where id = ?", Integer.class, tourId);
	}

	private long insert(String sql, Object... args) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) {
				statement.setObject(i + 1, args[i]);
			}
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

}
