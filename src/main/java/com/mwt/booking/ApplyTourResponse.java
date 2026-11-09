package com.mwt.booking;

import com.mwt.tour.TourStatus;

/**
 * 신청 결과(설계산출물 4.9 applyTour). status는 이번 신청 인원을 더한 뒤 상품의 상태로, 모집 중(RECRUITING)·확정(CONFIRMED)·
 * 마감(FULL) 가운데 하나다.
 */
public record ApplyTourResponse(Long bookingId, TourStatus status) {
}
