package com.mwt.booking;

import com.mwt.tour.TourStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청 결과(설계산출물 4.9 applyTour). status는 이번 신청 인원을 더한 뒤 상품의 상태로, 모집 중(RECRUITING)·확정(CONFIRMED)·
 * 마감(FULL) 가운데 하나다.
 */
@Schema(description = "신청 결과")
public record ApplyTourResponse(Long bookingId,
		@Schema(description = "이번 신청 인원을 더한 뒤의 상품 상태. RECRUITING·CONFIRMED·FULL 가운데 하나") TourStatus status) {
}
