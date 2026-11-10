package com.mwt.booking;

import java.time.LocalDate;

import com.mwt.OpenApiConfig;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이전 여행 하나(설계산출물 5.3, 분석산출물 MWT-UC3). 상품, 기간, 투어 등급, 가격을 담는다.
 * finalPrice는 신청할 때 정해진 가격(할인 뒤)이다.
 */
@Schema(description = "이전 여행 하나")
public record PreviousTourResponse(Long bookingId, Long tourId,
		@Schema(description = OpenApiConfig.TOUR_TITLE) String title,
		LocalDate departureDate,
		@Schema(description = OpenApiConfig.DURATION_DAYS) int duration,
		TourLevel level,
		@Schema(description = OpenApiConfig.LEVEL_NAME) String levelName,
		@Schema(description = "신청 인원(명)") int headcount,
		@Schema(description = "신청할 때 정해진 최종 가격(원, 할인 뒤)") long finalPrice) {

	static PreviousTourResponse from(Booking booking) {
		Tour tour = booking.getTour();
		return new PreviousTourResponse(booking.getId(), tour.getId(), TourSummary.title(tour), tour.getDepartureDate(),
				tour.getDuration(), booking.getLevel(), booking.getLevel().getDisplayName(), booking.getHeadcount(),
				booking.finalPrice());
	}

}
