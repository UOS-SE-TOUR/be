package com.mwt.tour;

/**
 * Tour.addApplicants가 돌려주는 확정 변화다(설계산출물 4.4). 확정 문자를 받을 사람을 정한다.
 */
public enum ConfirmChange {

	/** 아직 모집 중이다. 문자를 보내지 않는다. */
	NOT_CONFIRMED,
	/** 모집 중이던 상품이 이번 신청으로 확정·마감됐다. 그때까지의 신청자 모두에게 보낸다. */
	NEWLY_CONFIRMED,
	/** 이미 확정된 상품이다. 이번 신청자에게만 보낸다. */
	ALREADY_CONFIRMED

}
