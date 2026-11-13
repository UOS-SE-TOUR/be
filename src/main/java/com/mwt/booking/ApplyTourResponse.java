package com.mwt.booking;

import com.mwt.tour.TourStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청 결과(설계산출물 4.9 applyTour). status는 이번 신청 인원을 더한 뒤 상품의 상태로, 모집 중(RECRUITING)·확정(CONFIRMED)·
 * 마감(FULL) 가운데 하나다. 같은 신청 키로 다시 받은 결과는 첫 신청의 ID와 상품의 지금 상태라, 그사이 출발 처리되었으면
 * 취소(CANCELLED)·진행 중(IN_PROGRESS)·완료(COMPLETED)일 수도 있다(BookingService.applyTour).
 */
@Schema(description = "신청 결과")
public record ApplyTourResponse(Long bookingId,
		@Schema(description = "이번 신청 인원을 더한 뒤의 상품 상태. RECRUITING·CONFIRMED·FULL 가운데 하나. "
				+ "같은 신청 키로 다시 보내 받은 첫 신청의 결과는 상품의 지금 상태라 CANCELLED·IN_PROGRESS·COMPLETED일 수도 있다") TourStatus status) {
}
