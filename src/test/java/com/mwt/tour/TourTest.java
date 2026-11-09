package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.mwt.common.exception.BusinessException;

/**
 * 여행 상품의 업무 규칙(설계산출물 1.3 (1)·(3)·(5)·(6)·(7), 4.4 Tour, 5.8 부 흐름).
 */
class TourTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
	private static final LocalDate DEPARTURE = LocalDate.of(2026, 12, 1);
	private static final String BASE_TRANSPORT = "비행기 이코노미석";
	private static final String BUSINESS_TRANSPORT = "비행기 비즈니스석";

	@Nested
	class 상품_만들기 {

		@Test
		void 새_상품은_모집_중이고_신청_인원이_없다() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertThat(tour.getStatus()).isEqualTo(TourStatus.RECRUITING);
			assertThat(tour.getAppliedCount()).isZero();
			assertThat(tour.remainingSeats()).isEqualTo(10);
		}

		@Test
		void 테마가_비면_거부() {
			assertRejected(() -> new Tour(null, DEPARTURE, 5, 10, TODAY), "테마를 골라 주세요.");
		}

		@Test
		void 출발일이_비면_거부() {
			assertRejected(() -> new Tour(Theme.GOLF, null, 5, 10, TODAY), "출발일을 입력해 주세요.");
		}

		@ParameterizedTest
		@ValueSource(ints = {0, -3})
		void 출발일이_오늘이거나_지났으면_거부(int daysFromToday) {
			assertRejected(() -> new Tour(Theme.GOLF, TODAY.plusDays(daysFromToday), 5, 10, TODAY),
					"출발일은 오늘보다 뒤여야 합니다.");
		}

		@Test
		void 출발일이_내일이면_만든다() {
			assertThatCode(() -> new Tour(Theme.GOLF, TODAY.plusDays(1), 5, 10, TODAY)).doesNotThrowAnyException();
		}

		@ParameterizedTest
		@ValueSource(ints = {0, -1})
		void 기간이_1일_미만이면_거부(int duration) {
			assertRejected(() -> new Tour(Theme.GOLF, DEPARTURE, duration, 10, TODAY), "기간은 1일 이상이어야 합니다.");
		}

		@Test
		void 여행이_9999년_12월_31일을_넘으면_거부() {
			assertRejected(() -> new Tour(Theme.GOLF, LocalDate.of(9999, 12, 31), 2, 10, TODAY),
					"여행은 9999년 12월 31일 안에 끝나야 합니다.");
			assertRejected(() -> new Tour(Theme.GOLF, LocalDate.of(10000, 1, 1), 1, 10, TODAY),
					"여행은 9999년 12월 31일 안에 끝나야 합니다.");
			assertRejected(() -> new Tour(Theme.GOLF, DEPARTURE, Integer.MAX_VALUE, 10, TODAY),
					"여행은 9999년 12월 31일 안에 끝나야 합니다.");
			// 출발일이 LocalDate.MAX면 종료일 계산(plusDays)이 예외를 내므로 출발일부터 본다
			assertRejected(() -> new Tour(Theme.GOLF, LocalDate.MAX, 2, 10, TODAY),
					"여행은 9999년 12월 31일 안에 끝나야 합니다.");
			assertThatCode(() -> new Tour(Theme.GOLF, LocalDate.of(9999, 12, 30), 2, 10, TODAY))
				.doesNotThrowAnyException();
		}

		@Test
		void 기간이_1일이면_만든다() {
			assertThatCode(() -> new Tour(Theme.GOLF, DEPARTURE, 1, 10, TODAY)).doesNotThrowAnyException();
		}

		@ParameterizedTest
		@ValueSource(ints = {2, 0, -4})
		void 정원이_3명_미만이면_거부(int capacity) {
			assertRejected(() -> new Tour(Theme.GOLF, DEPARTURE, 5, capacity, TODAY), "정원은 3명 이상이어야 합니다.");
		}

		@Test
		void 정원이_3명이면_만든다() {
			assertThatCode(() -> new Tour(Theme.GOLF, DEPARTURE, 5, 3, TODAY)).doesNotThrowAnyException();
		}

		@ParameterizedTest
		@ValueSource(ints = {3, 5})
		void 허니문_정원이_홀수면_거부(int capacity) {
			assertRejected(() -> new Tour(Theme.HONEYMOON, DEPARTURE, 5, capacity, TODAY),
					"허니문 낭만 투어의 정원은 짝수여야 합니다.");
		}

		@Test
		void 허니문_정원이_짝수면_만든다() {
			assertThatCode(() -> new Tour(Theme.HONEYMOON, DEPARTURE, 5, 4, TODAY)).doesNotThrowAnyException();
		}

		@Test
		void 허니문이_아니면_정원이_홀수여도_만든다() {
			assertThatCode(() -> new Tour(Theme.PARENTS, DEPARTURE, 5, 5, TODAY)).doesNotThrowAnyException();
		}

	}

	@Nested
	class 등급별_가격 {

		@ParameterizedTest
		@EnumSource(value = Theme.class, names = {"HONEYMOON", "PARENTS"})
		void 허니문과_효도_투어에_클래식_가격이_오면_거부(Theme theme) {
			Tour tour = new Tour(theme, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addPrices(List.of(price(TourLevel.CLASSIC), price(TourLevel.GRAND),
					price(TourLevel.PREMIUM))), theme.getDisplayName() + "는 그랜드와 프리미엄 가격만 입력합니다.");
		}

		@ParameterizedTest
		@EnumSource(value = Theme.class, names = {"HONEYMOON", "PARENTS"})
		void 허니문과_효도_투어는_그랜드와_프리미엄_가격으로_등록(Theme theme) {
			Tour tour = new Tour(theme, DEPARTURE, 5, 10, TODAY);

			tour.addPrices(List.of(new TourPrice(TourLevel.GRAND, 1_500_000), new TourPrice(TourLevel.PREMIUM, 2_500_000)));

			assertThat(tour.getPrices()).extracting(TourPrice::getLevel, TourPrice::getPrice)
				.containsExactly(tuple(TourLevel.GRAND, 1_500_000L), tuple(TourLevel.PREMIUM, 2_500_000L));
		}

		@Test
		void 허용하는_등급의_가격이_빠지면_거부() {
			Tour golf = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);
			Tour honeymoon = new Tour(Theme.HONEYMOON, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> golf.addPrices(List.of(price(TourLevel.GRAND), price(TourLevel.PREMIUM))),
					"클래식 가격을 입력해 주세요.");
			assertRejected(() -> honeymoon.addPrices(List.of(price(TourLevel.GRAND))), "프리미엄 가격을 입력해 주세요.");
		}

		@Test
		void 같은_등급의_가격이_두_번이면_거부() {
			Tour tour = new Tour(Theme.HONEYMOON, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addPrices(List.of(price(TourLevel.GRAND), price(TourLevel.GRAND),
					price(TourLevel.PREMIUM))), "그랜드 가격이 두 번 들어 있습니다.");
		}

		@Test
		void 가격이_없으면_거부() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addPrices(List.of()), "등급별 가격을 입력해 주세요.");
			assertRejected(() -> tour.addPrices(null), "등급별 가격을 입력해 주세요.");
		}

		@ParameterizedTest
		@ValueSource(longs = {0, -1})
		void 가격이_1원_미만이면_거부(long price) {
			assertRejected(() -> new TourPrice(TourLevel.GRAND, price), "그랜드 가격은 1원 이상이어야 합니다.");
		}

		@Test
		void 등급이_비면_거부() {
			assertRejected(() -> new TourPrice(null, 1_000_000), "투어 등급을 골라 주세요.");
		}

		@Test
		void 가격은_한_번만_넣는다() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);
			tour.addPrices(allPrices(Theme.GOLF));

			assertThatThrownBy(() -> tour.addPrices(allPrices(Theme.GOLF))).isInstanceOf(IllegalStateException.class);
		}

	}

	@Nested
	class 옵션_목록 {

		@Test
		void 교통_선택지에_호텔_식사_추가_옵션을_1인_단가로_붙인다() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			tour.addOptions(transports());

			assertThat(tour.getOptions())
				.extracting(TourOption::getCategory, TourOption::getName, TourOption::getUnitPrice, TourOption::getDefaultFor)
				.containsExactly(
						tuple(OptionCategory.TRANSPORT, BASE_TRANSPORT, 0L, null),
						tuple(OptionCategory.TRANSPORT, BUSINESS_TRANSPORT, 300_000L, null),
						tuple(OptionCategory.HOTEL, "3등급 호텔", 100_000L, TourLevel.CLASSIC),
						tuple(OptionCategory.HOTEL, "4등급 호텔", 150_000L, TourLevel.GRAND),
						tuple(OptionCategory.HOTEL, "5등급 호텔", 250_000L, TourLevel.PREMIUM),
						tuple(OptionCategory.MEAL, "도시락", 10_000L, TourLevel.CLASSIC),
						tuple(OptionCategory.MEAL, "현지식 레스토랑", 30_000L, TourLevel.GRAND),
						tuple(OptionCategory.MEAL, "고급 레스토랑", 80_000L, TourLevel.PREMIUM),
						tuple(OptionCategory.EXTRA, "샴페인", 50_000L, TourLevel.PREMIUM),
						tuple(OptionCategory.EXTRA, "커피", 5_000L, null));
		}

		@ParameterizedTest
		@EnumSource(value = Theme.class, names = {"HONEYMOON", "PARENTS"})
		void 허니문과_효도_투어에는_클래식의_3등급_호텔과_도시락을_넣지_않는다(Theme theme) {
			Tour tour = new Tour(theme, DEPARTURE, 5, 10, TODAY);

			tour.addOptions(transports());

			assertThat(tour.getOptions()).filteredOn(option -> option.getCategory() == OptionCategory.HOTEL)
				.extracting(TourOption::getName)
				.containsExactly("4등급 호텔", "5등급 호텔");
			assertThat(tour.getOptions()).filteredOn(option -> option.getCategory() == OptionCategory.MEAL)
				.extracting(TourOption::getName)
				.containsExactly("현지식 레스토랑", "고급 레스토랑");
		}

		@Test
		void 추가_요금이_0원인_기본_교통이_없으면_거부() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addOptions(List.of(TourOption.transport(BUSINESS_TRANSPORT, 300_000))),
					"추가 요금이 0원인 기본 교통을 넣어 주세요.");
		}

		@Test
		void 추가_요금이_0원인_기본_교통이_둘이면_거부() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addOptions(List.of(TourOption.transport(BASE_TRANSPORT, 0),
					TourOption.transport("고속버스", 0))), "추가 요금이 0원인 기본 교통은 하나만 넣을 수 있습니다.");
		}

		@Test
		void 교통_선택지가_없으면_거부() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addOptions(List.of()), "교통 선택지를 입력해 주세요.");
			assertRejected(() -> tour.addOptions(null), "교통 선택지를 입력해 주세요.");
		}

		@Test
		void 같은_이름의_교통_선택지는_거부() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);

			assertRejected(() -> tour.addOptions(List.of(TourOption.transport(BASE_TRANSPORT, 0),
					TourOption.transport(" " + BASE_TRANSPORT, 100_000))),
					"같은 이름의 교통 선택지가 두 개 있습니다: " + BASE_TRANSPORT);
		}

		@Test
		void 교통_추가_요금이_0원_미만이면_거부() {
			assertRejected(() -> TourOption.transport("고속버스", -10_000), "교통 추가 요금은 0원 이상이어야 합니다.");
		}

		@ParameterizedTest
		@ValueSource(strings = {"", "   "})
		void 교통_이름이_비면_거부(String name) {
			assertRejected(() -> TourOption.transport(name, 0), "교통 이름을 입력해 주세요.");
			assertRejected(() -> TourOption.transport(null, 0), "교통 이름을 입력해 주세요.");
		}

		@Test
		void 교통_이름은_100자까지() {
			assertThat(TourOption.transport("가".repeat(100), 0).getName()).hasSize(100);
			assertRejected(() -> TourOption.transport("가".repeat(101), 0), "교통 이름은 100자까지 쓸 수 있습니다.");
		}

		@Test
		void 교통_이름의_길이는_DB처럼_글자_수로_센다() {
			assertThatCode(() -> TourOption.transport("🚌".repeat(100), 0)).doesNotThrowAnyException();
			assertRejected(() -> TourOption.transport("🚌".repeat(101), 0), "교통 이름은 100자까지 쓸 수 있습니다.");
		}

		@Test
		void 교통_이름의_앞뒤_공백은_지운다() {
			assertThat(TourOption.transport("  고속버스 ", 0).getName()).isEqualTo("고속버스");
		}

		@Test
		void 옵션은_한_번만_넣는다() {
			Tour tour = new Tour(Theme.GOLF, DEPARTURE, 5, 10, TODAY);
			tour.addOptions(transports());

			assertThatThrownBy(() -> tour.addOptions(transports())).isInstanceOf(IllegalStateException.class);
		}

	}

	@Nested
	class 옵션_요금_차이 {

		private final Tour golf = savedTour(Theme.GOLF, 10);

		@Test
		void 그랜드에서_5등급_호텔은_100000원을_더한다() {
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "5등급 호텔"))).isEqualTo(100_000);
		}

		@Test
		void 그랜드에서_3등급_호텔은_50000원을_뺀다() {
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "3등급 호텔"))).isEqualTo(-50_000);
		}

		@Test
		void 등급의_기본_호텔과_식사는_0원() {
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "4등급 호텔"))).isZero();
			assertThat(golf.optionPriceDiff(TourLevel.PREMIUM, optionId(golf, "고급 레스토랑"))).isZero();
		}

		@Test
		void 그랜드에서_도시락은_20000원을_빼고_고급_레스토랑은_50000원을_더한다() {
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "도시락"))).isEqualTo(-20_000);
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "고급 레스토랑"))).isEqualTo(50_000);
		}

		@Test
		void 프리미엄의_샴페인은_0원이고_다른_등급은_단가_그대로() {
			assertThat(golf.optionPriceDiff(TourLevel.PREMIUM, optionId(golf, "샴페인"))).isZero();
			assertThat(golf.optionPriceDiff(TourLevel.CLASSIC, optionId(golf, "샴페인"))).isEqualTo(50_000);
			assertThat(golf.optionPriceDiff(TourLevel.GRAND, optionId(golf, "샴페인"))).isEqualTo(50_000);
		}

		@Test
		void 커피는_어느_등급이든_단가_그대로() {
			assertThat(golf.optionPriceDiff(TourLevel.PREMIUM, optionId(golf, "커피"))).isEqualTo(5_000);
		}

		@Test
		void 교통은_기본_교통_대비_추가_요금_그대로() {
			assertThat(golf.optionPriceDiff(TourLevel.CLASSIC, optionId(golf, BASE_TRANSPORT))).isZero();
			assertThat(golf.optionPriceDiff(TourLevel.CLASSIC, optionId(golf, BUSINESS_TRANSPORT))).isEqualTo(300_000);
		}

		@Test
		void 이_상품에_없는_옵션은_거부() {
			assertRejected(() -> golf.optionPriceDiff(TourLevel.GRAND, 999L), "이 상품에 없는 옵션입니다.");
		}

		@Test
		void 테마가_허용하지_않는_등급은_거부() {
			Tour honeymoon = savedTour(Theme.HONEYMOON, 10);

			assertRejected(() -> honeymoon.optionPriceDiff(TourLevel.CLASSIC, optionId(honeymoon, "샴페인")),
					"허니문 낭만 투어는 그랜드와 프리미엄만 고를 수 있습니다.");
		}

	}

	@Nested
	class 신청_확인 {

		private final Tour golf = savedTour(Theme.GOLF, 10);

		@Test
		void 조건에_맞으면_통과() {
			assertThatCode(() -> golf.checkApplicable(TourLevel.CLASSIC, 3,
					List.of(optionId(golf, "5등급 호텔"), optionId(golf, "샴페인"), optionId(golf, "커피")), TODAY))
				.doesNotThrowAnyException();
		}

		@Test
		void 옵션을_고르지_않아도_통과() {
			assertThatCode(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY)).doesNotThrowAnyException();
			assertThatCode(() -> golf.checkApplicable(TourLevel.GRAND, 1, null, TODAY)).doesNotThrowAnyException();
		}

		@Test
		void 출발일이_오늘이거나_지났으면_거부() {
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), DEPARTURE),
					"출발일이 오늘이거나 지난 상품은 신청할 수 없습니다.");
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), DEPARTURE.plusDays(1)),
					"출발일이 오늘이거나 지난 상품은 신청할 수 없습니다.");
		}

		@ParameterizedTest
		@ValueSource(ints = {0, -1})
		void 인원이_1명_미만이면_거부(int headcount) {
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, headcount, List.of(), TODAY),
					"인원은 1명 이상이어야 합니다.");
		}

		@Test
		void 남은_자리만큼은_신청할_수_있다() {
			ReflectionTestUtils.setField(golf, "appliedCount", 7);

			assertThat(golf.remainingSeats()).isEqualTo(3);
			assertThatCode(() -> golf.checkApplicable(TourLevel.GRAND, 3, List.of(), TODAY)).doesNotThrowAnyException();
		}

		@Test
		void 남은_자리보다_많으면_거부() {
			ReflectionTestUtils.setField(golf, "appliedCount", 7);

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 4, List.of(), TODAY), "남은 자리보다 인원이 많습니다.");
		}

		@Test
		void 남은_자리가_없으면_마감() {
			ReflectionTestUtils.setField(golf, "appliedCount", 10);

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY), "마감된 상품입니다.");
		}

		@Test
		void 마감_상태면_자리가_남아_보여도_거부() {
			ReflectionTestUtils.setField(golf, "status", TourStatus.FULL);

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY), "마감된 상품입니다.");
		}

		@ParameterizedTest
		@EnumSource(value = TourStatus.class, names = {"CANCELLED", "IN_PROGRESS", "COMPLETED"})
		void 모집_중이나_확정이_아니면_거부(TourStatus status) {
			ReflectionTestUtils.setField(golf, "status", status);

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY), "신청할 수 없는 상품입니다.");
		}

		@Test
		void 신청할_수_없는_상태면_자리가_다_찼어도_마감이라고_하지_않는다() {
			ReflectionTestUtils.setField(golf, "status", TourStatus.CANCELLED);
			ReflectionTestUtils.setField(golf, "appliedCount", 10);

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY), "신청할 수 없는 상품입니다.");
		}

		@Test
		void 확정된_상품도_자리가_남으면_신청할_수_있다() {
			ReflectionTestUtils.setField(golf, "status", TourStatus.CONFIRMED);
			ReflectionTestUtils.setField(golf, "appliedCount", 3);

			assertThatCode(() -> golf.checkApplicable(TourLevel.GRAND, 2, List.of(), TODAY)).doesNotThrowAnyException();
		}

		@Test
		void 등급이_비면_거부() {
			assertRejected(() -> golf.checkApplicable(null, 1, List.of(), TODAY), "투어 등급을 골라 주세요.");
		}

		@ParameterizedTest
		@EnumSource(value = Theme.class, names = {"HONEYMOON", "PARENTS"})
		void 허니문과_효도_투어에서_클래식은_거부(Theme theme) {
			Tour tour = savedTour(theme, 10);

			assertRejected(() -> tour.checkApplicable(TourLevel.CLASSIC, 2, List.of(), TODAY),
					theme.getDisplayName() + "는 그랜드와 프리미엄만 고를 수 있습니다.");
		}

		@Test
		void 허니문은_2명만() {
			Tour honeymoon = savedTour(Theme.HONEYMOON, 10);

			assertThatCode(() -> honeymoon.checkApplicable(TourLevel.GRAND, 2, List.of(), TODAY)).doesNotThrowAnyException();
			assertRejected(() -> honeymoon.checkApplicable(TourLevel.GRAND, 1, List.of(), TODAY),
					"허니문 낭만 투어는 2명만 신청할 수 있습니다.");
			assertRejected(() -> honeymoon.checkApplicable(TourLevel.GRAND, 4, List.of(), TODAY),
					"허니문 낭만 투어는 2명만 신청할 수 있습니다.");
		}

		@Test
		void 다른_상품의_옵션은_거부() {
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(999L), TODAY), "이 상품에 없는 옵션입니다.");
		}

		@Test
		void 같은_옵션을_두_번_고르면_거부() {
			Long champagne = optionId(golf, "샴페인");

			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1, List.of(champagne, champagne), TODAY),
					"같은 옵션을 두 번 고를 수 없습니다.");
		}

		@Test
		void 호텔_교통_식사는_종류마다_하나만() {
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1,
					List.of(optionId(golf, "3등급 호텔"), optionId(golf, "5등급 호텔")), TODAY),
					"호텔·교통·식사는 종류마다 하나만 고를 수 있습니다.");
			assertRejected(() -> golf.checkApplicable(TourLevel.GRAND, 1,
					List.of(optionId(golf, BASE_TRANSPORT), optionId(golf, BUSINESS_TRANSPORT)), TODAY),
					"호텔·교통·식사는 종류마다 하나만 고를 수 있습니다.");
		}

	}

	private static void assertRejected(ThrowingCallable call, String message) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class).hasMessage(message);
	}

	private static TourPrice price(TourLevel level) {
		return new TourPrice(level, 1_000_000);
	}

	private static List<TourPrice> allPrices(Theme theme) {
		List<TourPrice> prices = new ArrayList<>();
		for (TourLevel level : TourLevel.values()) {
			if (theme.allows(level)) {
				prices.add(price(level));
			}
		}
		return prices;
	}

	private static List<TourOption> transports() {
		return List.of(TourOption.transport(BASE_TRANSPORT, 0), TourOption.transport(BUSINESS_TRANSPORT, 300_000));
	}

	/** 저장한 상품처럼 옵션마다 ID를 붙인다. */
	private static Tour savedTour(Theme theme, int capacity) {
		Tour tour = new Tour(theme, DEPARTURE, 5, capacity, TODAY);
		tour.addPrices(allPrices(theme));
		tour.addOptions(transports());
		long id = 1;
		for (TourOption option : tour.getOptions()) {
			ReflectionTestUtils.setField(option, "id", id++);
		}
		return tour;
	}

	private static Long optionId(Tour tour, String name) {
		return tour.getOptions().stream()
			.filter(option -> option.getName().equals(name))
			.findFirst()
			.orElseThrow()
			.getId();
	}

}
