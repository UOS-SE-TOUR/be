package com.mwt.tour;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 여행 상품의 상태다. 뜻과 전이는 설계산출물 6장 Tour 객체 상태도에 있다.
 */
@Schema(enumAsRef = true, description = "상품 상태. RECRUITING(모집 중: 신청 인원 3명 미만), "
		+ "CONFIRMED(확정: 3명 이상이고 자리가 남음), FULL(마감: 정원이 다 참), "
		+ "CANCELLED(취소: 출발일까지 3명이 안 됨), IN_PROGRESS(진행 중: 출발함), COMPLETED(완료: 여행 종료일이 지남)")
public enum TourStatus {

	RECRUITING,
	CONFIRMED,
	FULL,
	CANCELLED,
	IN_PROGRESS,
	COMPLETED

}
