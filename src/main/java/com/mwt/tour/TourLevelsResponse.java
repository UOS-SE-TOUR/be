package com.mwt.tour;

import java.util.List;

/**
 * 한 상품에서 고를 수 있는 투어 등급과 등급별 1인 가격·제공 내용(설계산출물 5.4 getLevels).
 * service·souvenir·vehicle은 등급과 상관없이 그 테마가 제공하는 테마 서비스·기념품·현지 차량이다(분석산출물 1.4 표 2).
 */
public record TourLevelsResponse(String service, String souvenir, String vehicle, List<Level> levels) {

	/** 투어 등급 하나. hotelGrade·meal·includesChampagne은 그 등급이 기본으로 주는 호텔 등급·식사·샴페인이다. */
	public record Level(TourLevel level, String name, long price, int hotelGrade, String meal,
			boolean includesChampagne) {
	}

}
