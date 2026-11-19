package com.mwt.booking;

import java.time.Instant;
import java.time.LocalDate;

import com.mwt.common.docs.ApiDescriptions;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourStatus;
import com.mwt.tour.TourSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 신청 하나(MWT-UC11). 이전 여행 한 줄(PreviousTourResponse)의 칸에 신청한 때, 상품의 지금 상태, 확정까지 남은 인원을 더했다.
 */
@Schema(description = "내 신청 하나")
public record MyBookingResponse(Long bookingId, Long tourId,
		@Schema(description = ApiDescriptions.TOUR_TITLE) String title,
		LocalDate departureDate,
		@Schema(description = ApiDescriptions.DURATION_DAYS) int duration,
		TourLevel level,
		@Schema(description = ApiDescriptions.LEVEL_NAME) String levelName,
		@Schema(description = "신청 인원(명)") int headcount,
		@Schema(description = "신청할 때 정해진 최종 가격(원, 할인 뒤)") long finalPrice,
		@Schema(description = "신청한 때(UTC, ISO 8601, 예: 2026-11-20T03:15:00.123456Z). 초 아래 자리는 없거나 자릿수가 다를 수 있다") Instant appliedAt,
		@Schema(description = "상품의 지금 상태") TourStatus status,
		@Schema(description = "확정까지 남은 인원(명). 모집 중(RECRUITING)일 때만 1 이상이고 다른 상태에서는 0. 허니문은 2명 단위") int headcountToConfirm) {

	static MyBookingResponse from(Booking booking) {
		Tour tour = booking.getTour();
		return new MyBookingResponse(booking.getId(), tour.getId(), TourSummary.title(tour), tour.getDepartureDate(),
				tour.getDuration(), booking.getLevel(), booking.getLevel().getDisplayName(), booking.getHeadcount(),
				booking.finalPrice(), booking.getAppliedAt(), tour.getStatus(), tour.headcountToConfirm());
	}

}
