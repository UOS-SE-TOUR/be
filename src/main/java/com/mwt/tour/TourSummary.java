package com.mwt.tour;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 상품 목록의 한 줄이다(설계산출물 4.4). 음성 경로의 후보 상품(TourSelection.candidates)도 이 모양이다.
 * title은 테마 이름에 출발일과 기간을 붙인 상품 이름이다(예: "허니문 낭만 투어 10/20 출발 5일").
 */
public record TourSummary(Long tourId, String title, LocalDate departureDate, int duration, int remainingSeats,
		TourStatus status) {

	private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");

	static TourSummary from(Tour tour) {
		String title = tour.getTheme().getDisplayName() + " " + tour.getDepartureDate().format(TITLE_DATE) + " 출발 "
				+ tour.getDuration() + "일";
		return new TourSummary(tour.getId(), title, tour.getDepartureDate(), tour.getDuration(), tour.remainingSeats(),
				tour.getStatus());
	}

}
