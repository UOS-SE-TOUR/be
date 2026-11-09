package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TourSummaryTest {

	@Test
	void 상품_이름은_테마_이름에_출발일과_기간을_붙인다() {
		Tour tour = new Tour(Theme.HONEYMOON, LocalDate.of(2026, 10, 20), 5, 4, LocalDate.of(2026, 10, 1));

		TourSummary summary = TourSummary.from(tour);

		assertThat(summary.title()).isEqualTo("허니문 낭만 투어 10/20 출발 5일");
		assertThat(summary.departureDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(summary.duration()).isEqualTo(5);
		assertThat(summary.remainingSeats()).isEqualTo(4);
		assertThat(summary.status()).isEqualTo(TourStatus.RECRUITING);
	}

	@Test
	void 한_자리_월과_일은_앞에_0을_붙이지_않는다() {
		Tour tour = new Tour(Theme.GOLF, LocalDate.of(2027, 1, 5), 1, 10, LocalDate.of(2026, 12, 1));

		assertThat(TourSummary.from(tour).title()).isEqualTo("골프 챌린지 투어 1/5 출발 1일");
	}

}
