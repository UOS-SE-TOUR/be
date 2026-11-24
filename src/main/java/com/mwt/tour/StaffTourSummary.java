package com.mwt.tour;

import java.time.LocalDate;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 직원 상품 현황의 한 줄이다. 고객 목록(TourSummary)과 달리 모든 상태의 상품이 오고, 정원과 신청 인원을 함께 준다.
 */
@Schema(description = "직원 상품 현황의 한 줄")
public record StaffTourSummary(Long tourId,
		Theme theme,
		@Schema(description = ApiDescriptions.TOUR_TITLE) String title,
		LocalDate departureDate,
		@Schema(description = ApiDescriptions.DURATION_DAYS) int duration,
		@Schema(description = "정원(명)") int capacity,
		@Schema(description = "신청 인원(명)") int appliedCount,
		TourStatus status) {

	public static StaffTourSummary from(Tour tour) {
		return new StaffTourSummary(tour.getId(), tour.getTheme(), TourSummary.title(tour), tour.getDepartureDate(),
				tour.getDuration(), tour.getCapacity(), tour.getAppliedCount(), tour.getStatus());
	}

}
