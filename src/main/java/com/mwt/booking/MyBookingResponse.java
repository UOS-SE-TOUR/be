package com.mwt.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.mwt.common.docs.ApiDescriptions;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourStatus;
import com.mwt.tour.TourSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 신청 하나(MWT-UC11). 이전 여행 한 줄(PreviousTourResponse)의 칸에 신청한 때, 상품의 지금 상태, 확정까지 남은 인원을 더하고,
 * 신청 내용 바꾸기(MWT-UC12)에 쓰는 지금 고른 옵션과 지금 바꿀 수 있는지를 더했다. 신청을 바꾸면 바뀐 신청도 이 모양으로 돌려준다.
 * finalPrice는 신청하거나 마지막으로 바꿀 때 정해진 가격(할인 뒤)이다.
 */
@Schema(description = "내 신청 하나")
public record MyBookingResponse(Long bookingId, Long tourId,
		@Schema(description = ApiDescriptions.TOUR_TITLE) String title,
		LocalDate departureDate,
		@Schema(description = ApiDescriptions.DURATION_DAYS) int duration,
		TourLevel level,
		@Schema(description = ApiDescriptions.LEVEL_NAME) String levelName,
		@Schema(description = "신청 인원(명)") int headcount,
		@Schema(description = "지금 고른 옵션의 ID(옵션 목록의 optionId), 작은 순. 고르지 않은 종류는 그 등급의 기본 제공이다. 고른 옵션이 없으면 빈 목록")
		List<Long> optionIds,
		@Schema(description = "신청하거나 마지막으로 바꿀 때 정해진 최종 가격(원, 할인 뒤)") long finalPrice,
		@Schema(description = "신청한 때(UTC, ISO 8601, 예: 2026-11-20T03:15:00.123456Z). 초 아래 자리는 없거나 자릿수가 다를 수 있다. 신청을 바꿔도 그대로다") Instant appliedAt,
		@Schema(description = "상품의 지금 상태") TourStatus status,
		@Schema(description = "확정까지 남은 인원(명). 모집 중(RECRUITING)일 때만 1 이상이고 다른 상태에서는 0. 허니문은 2명 단위") int headcountToConfirm,
		@Schema(description = "지금 투어 등급과 옵션을 바꿀 수 있는지. 출발일이 오늘보다 뒤이고 상품이 모집 중·확정·마감(RECRUITING·CONFIRMED·FULL)이면 true")
		boolean changeable) {

	static MyBookingResponse from(Booking booking, LocalDate today) {
		Tour tour = booking.getTour();
		List<Long> optionIds = booking.getOptions().stream()
			.map(TourOption::getId)
			.sorted()
			.toList();
		return new MyBookingResponse(booking.getId(), tour.getId(), TourSummary.title(tour), tour.getDepartureDate(),
				tour.getDuration(), booking.getLevel(), booking.getLevel().getDisplayName(), booking.getHeadcount(), optionIds,
				booking.finalPrice(), booking.getAppliedAt(), tour.getStatus(), tour.headcountToConfirm(),
				tour.isChangeable(today));
	}

}
