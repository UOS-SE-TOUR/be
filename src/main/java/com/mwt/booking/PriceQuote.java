package com.mwt.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.mwt.tour.OptionCategory;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourSummary;

/**
 * 가격 계산 결과다(설계산출물 4.5). 고른 내용, 할인 전 총 가격, 할인 금액(없으면 0), 최종 가격(totalPrice − discount)을 담는다.
 */
public record PriceQuote(Summary summary, long totalPrice, long discount, long finalPrice) {

	static PriceQuote of(Tour tour, TourLevel level, int headcount, Collection<Long> optionIds, long totalPrice,
			long discount) {
		List<Option> options = tour.getOptions().stream()
			.filter(option -> optionIds.contains(option.getId()))
			.sorted(Comparator.comparing(TourOption::getCategory).thenComparing(TourOption::getId))
			.map(option -> new Option(option.getId(), option.getCategory(), option.getName(),
					tour.optionPriceDiff(level, option.getId())))
			.toList();
		Summary summary = new Summary(tour.getId(), TourSummary.title(tour), tour.getDepartureDate(), tour.getDuration(),
				level, level.getDisplayName(), headcount, options);
		return new PriceQuote(summary, totalPrice, discount, totalPrice - discount);
	}

	/** 고른 내용. options는 바꾼 옵션만 종류 순(호텔, 교통, 식사, 추가)으로 담고, 고르지 않은 종류는 그 등급의 기본 제공이다. */
	public record Summary(Long tourId, String title, LocalDate departureDate, int duration, TourLevel level,
			String levelName, int headcount, List<Option> options) {
	}

	/** 고른 옵션 하나. priceDiff는 고른 투어 등급을 기준으로 한 1인 요금 차이(Tour.optionPriceDiff)다. */
	public record Option(Long optionId, OptionCategory category, String name, long priceDiff) {
	}

}
