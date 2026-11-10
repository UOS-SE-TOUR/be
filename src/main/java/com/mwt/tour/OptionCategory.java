package com.mwt.tour;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 옵션 종류다. EXTRA는 추가 옵션(샴페인, 커피)이다(설계산출물 4.4).
 */
@Schema(enumAsRef = true, description = "옵션 종류. HOTEL(호텔), TRANSPORT(교통), MEAL(식사), EXTRA(추가 옵션: 샴페인·커피)")
public enum OptionCategory {

	HOTEL,
	TRANSPORT,
	MEAL,
	EXTRA

}
