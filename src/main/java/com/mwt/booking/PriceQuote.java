package com.mwt.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.mwt.common.docs.ApiDescriptions;
import com.mwt.tour.OptionCategory;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가격 계산 결과다(설계산출물 4.5). 고른 내용, 할인 전 총 가격, 할인 금액(없으면 0), 최종 가격(totalPrice − discount)을 담는다.
 */
@Schema(description = "가격 계산 결과")
public record PriceQuote(@Schema(description = "고른 내용") Summary summary,
		@Schema(description = "할인 전 총 가격(원)") long totalPrice,
		@Schema(description = "단골 할인 금액(원). 할인이 없으면 0") long discount,
		@Schema(description = "최종 가격(원). totalPrice − discount") long finalPrice) {

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

	/**
	 * 고른 내용. options는 요청의 optionIds를 종류 순(호텔, 교통, 식사, 추가)으로 담는다. 그 등급의 기본 제공을 골랐으면 priceDiff가 0이고,
	 * 고르지 않은 종류는 그 등급의 기본 제공(교통은 기본 교통)이다.
	 */
	@Schema(name = "QuoteSummary",
			description = "고른 내용. 고르지 않은 옵션 종류는 그 등급의 기본 제공(교통은 기본 교통)으로 계산한다")
	public record Summary(Long tourId,
			@Schema(description = ApiDescriptions.TOUR_TITLE) String title,
			LocalDate departureDate,
			@Schema(description = ApiDescriptions.DURATION_DAYS) int duration,
			TourLevel level,
			@Schema(description = ApiDescriptions.LEVEL_NAME) String levelName,
			@Schema(description = "인원(명)") int headcount,
			@Schema(description = "요청의 optionIds를 종류 순(호텔, 교통, 식사, 추가)으로 담은 것. 고르지 않았으면 빈 목록")
			List<Option> options) {
	}

	/** 고른 옵션 하나. priceDiff는 고른 투어 등급을 기준으로 한 1인 요금 차이(Tour.optionPriceDiff)다. */
	@Schema(name = "SelectedOption", description = "고른 옵션 하나")
	public record Option(Long optionId, OptionCategory category, String name,
			@Schema(description = "고른 투어 등급을 기준으로 한 1인 요금 차이(원). 그 등급의 기본 제공이면 0, 더 싼 호텔·식사면 음수")
			long priceDiff) {
	}

}
