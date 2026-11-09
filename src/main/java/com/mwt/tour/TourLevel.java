package com.mwt.tour;

import lombok.Getter;

/**
 * 투어 등급과 그 등급이 기본으로 주는 호텔 등급·식사·샴페인이다(설계산출물 4.4).
 * meal은 식사 옵션(TourOption)의 이름과 같은 값이다.
 */
@Getter
public enum TourLevel {

	CLASSIC("클래식", 3, "도시락", false),
	GRAND("그랜드", 4, "현지식 레스토랑", false),
	PREMIUM("프리미엄", 5, "고급 레스토랑", true);

	private final String displayName;
	private final int hotelGrade;
	private final String meal;
	private final boolean includesChampagne;

	TourLevel(String displayName, int hotelGrade, String meal, boolean includesChampagne) {
		this.displayName = displayName;
		this.hotelGrade = hotelGrade;
		this.meal = meal;
		this.includesChampagne = includesChampagne;
	}

}
