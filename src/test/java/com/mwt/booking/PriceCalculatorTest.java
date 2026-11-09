package com.mwt.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourPrice;

/**
 * 할인 전 총 가격 계산(설계산출물 1.3 (6), 4.5 PriceCalculator). 등급별 1인 가격은 클래식 900,000원, 그랜드 1,500,000원,
 * 프리미엄 2,500,000원이고, 교통은 기본(이코노미석) 0원과 비즈니스석 300,000원이다.
 */
class PriceCalculatorTest {

	private final PriceCalculator calculator = new PriceCalculator();
	private final Tour golf = savedTour(900_000, 1_500_000, 2_500_000);

	@Test
	void 옵션을_바꾸지_않으면_등급_가격에_인원을_곱한다() {
		assertThat(calculator.calculate(golf, TourLevel.GRAND, 3, List.of())).isEqualTo(4_500_000);
	}

	@Test
	void 그랜드에서_5등급_호텔은_1인_100000원을_더한다() {
		assertThat(calculator.calculate(golf, TourLevel.GRAND, 2, List.of(optionId(golf, "5등급 호텔"))))
			.isEqualTo((1_500_000 + 100_000) * 2);
	}

	@Test
	void 클래식에서_샴페인은_1인_50000원을_더한다() {
		assertThat(calculator.calculate(golf, TourLevel.CLASSIC, 1, List.of(optionId(golf, "샴페인"))))
			.isEqualTo(900_000 + 50_000);
	}

	@Test
	void 프리미엄의_샴페인은_기본_제공이라_0원() {
		assertThat(calculator.calculate(golf, TourLevel.PREMIUM, 1, List.of(optionId(golf, "샴페인"))))
			.isEqualTo(2_500_000);
	}

	@Test
	void 기본_교통은_0원이고_다른_교통은_추가_요금을_더한다() {
		assertThat(calculator.calculate(golf, TourLevel.CLASSIC, 1, List.of(optionId(golf, "비행기 이코노미석"))))
			.isEqualTo(900_000);
		assertThat(calculator.calculate(golf, TourLevel.CLASSIC, 1, List.of(optionId(golf, "비행기 비즈니스석"))))
			.isEqualTo(900_000 + 300_000);
	}

	@Test
	void 낮은_호텔과_식사는_차이만큼_빼고_옵션마다_더한_1인_가격에_인원을_곱한다() {
		List<Long> options = List.of(optionId(golf, "3등급 호텔"), optionId(golf, "도시락"), optionId(golf, "커피"),
				optionId(golf, "비행기 비즈니스석"));

		assertThat(calculator.calculate(golf, TourLevel.GRAND, 4, options))
			.isEqualTo((1_500_000 - 50_000 - 20_000 + 5_000 + 300_000) * 4);
	}

	@Test
	void 낮은_옵션으로_1인_가격이_0원_미만이_되면_다른_옵션을_고르라고_거부() {
		Tour cheap = savedTour(100_000, 100_000, 100_000);

		assertThatThrownBy(() -> calculator.calculate(cheap, TourLevel.PREMIUM, 1,
				List.of(optionId(cheap, "3등급 호텔"), optionId(cheap, "도시락"))))
			.isInstanceOf(BusinessException.class)
			.hasMessage("고른 옵션으로는 가격을 계산할 수 없습니다. 다른 옵션을 골라 주세요.");
	}

	@Test
	void 낮은_옵션을_빼도_1인_가격이_0원이면_계산한다() {
		Tour cheap = savedTour(100_000, 100_000, 220_000);

		assertThat(calculator.calculate(cheap, TourLevel.PREMIUM, 2,
				List.of(optionId(cheap, "3등급 호텔"), optionId(cheap, "도시락"))))
			.isZero();
	}

	/** DB에 저장한 것처럼 옵션에 ID를 붙인다. */
	private static Tour savedTour(long classic, long grand, long premium) {
		Tour tour = new Tour(Theme.GOLF, LocalDate.of(2026, 12, 1), 5, 10, LocalDate.of(2026, 11, 10));
		tour.addPrices(List.of(new TourPrice(TourLevel.CLASSIC, classic), new TourPrice(TourLevel.GRAND, grand),
				new TourPrice(TourLevel.PREMIUM, premium)));
		tour.addOptions(List.of(TourOption.transport("비행기 이코노미석", 0), TourOption.transport("비행기 비즈니스석", 300_000)));
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
