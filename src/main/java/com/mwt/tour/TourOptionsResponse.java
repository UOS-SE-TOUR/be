package com.mwt.tour;

import java.util.List;

/**
 * 한 상품의 옵션 목록을 종류별로 나눈 것(설계산출물 5.6 getOptions). 종류 안에서는 등록한 순서다.
 */
public record TourOptionsResponse(List<Option> hotels, List<Option> transports, List<Option> meals,
		List<Option> extras) {

	/**
	 * 옵션 하나. priceDiff는 받은 투어 등급을 기준으로 한 1인 요금 차이(Tour.optionPriceDiff)다.
	 * 그 등급의 기본 호텔·식사, 기본 교통, 그 등급이 기본으로 주는 추가 옵션(프리미엄의 샴페인)은 0이다.
	 */
	public record Option(Long optionId, String name, long priceDiff) {
	}

}
