package com.mwt.tour;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상품 목록의 한 줄이다(설계산출물 4.4). 음성 경로의 후보 상품(TourSelection.candidates)도 이 모양이다.
 * title은 테마 이름에 출발일과 기간을 붙인 상품 이름이다(예: "허니문 낭만 투어 10/20 출발 5일").
 */
@Schema(description = "상품 목록의 한 줄(음성으로 고르기의 후보 상품도 이 모양)")
public record TourSummary(Long tourId,
		@Schema(description = ApiDescriptions.TOUR_TITLE) String title,
		LocalDate departureDate,
		@Schema(description = ApiDescriptions.DURATION_DAYS) int duration,
		@Schema(description = "남은 자리(명)") int remainingSeats,
		TourStatus status) {

	private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");

	static TourSummary from(Tour tour) {
		return new TourSummary(tour.getId(), title(tour), tour.getDepartureDate(), tour.getDuration(),
				tour.remainingSeats(), tour.getStatus());
	}

	/** 상품 이름. booking 모듈의 가격 계산 결과와 이전 여행도 같은 이름을 쓴다. 형식을 바꾸면 ApiDescriptions.TOUR_TITLE도 고친다. */
	public static String title(Tour tour) {
		return tour.getTheme().getDisplayName() + " " + tour.getDepartureDate().format(TITLE_DATE) + " 출발 "
				+ tour.getDuration() + "일";
	}

}
