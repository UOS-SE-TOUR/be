package com.mwt.tour;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 상품의 옵션 목록을 종류별로 나눈 것(설계산출물 5.6 getOptions). 종류 안에서는 등록한 순서다.
 */
@Schema(description = "한 상품의 옵션 목록을 종류별로 나눈 것. 종류 안에서는 등록한 순서")
public record TourOptionsResponse(List<Option> hotels, List<Option> transports, List<Option> meals,
		@Schema(description = "추가 옵션(샴페인·커피)") List<Option> extras) {

	/**
	 * 옵션 하나. priceDiff는 받은 투어 등급을 기준으로 한 1인 요금 차이(Tour.optionPriceDiff)다.
	 * 그 등급의 기본 호텔·식사, 기본 교통, 그 등급이 기본으로 주는 추가 옵션(프리미엄의 샴페인)은 0이다.
	 */
	@Schema(name = "TourOption", description = "옵션 하나")
	public record Option(Long optionId, String name,
			@Schema(description = "받은 투어 등급을 기준으로 한 1인 요금 차이(원). 그 등급의 기본 호텔·식사, 기본 교통, "
					+ "그 등급이 기본으로 주는 추가 옵션(프리미엄의 샴페인)은 0이고, 더 싼 호텔·식사면 음수")
			long priceDiff) {
	}

}
