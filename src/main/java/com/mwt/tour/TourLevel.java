package com.mwt.tour;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 투어 등급과 그 등급이 기본으로 주는 호텔 등급·식사·샴페인이다(설계산출물 4.4).
 * meal은 식사 옵션(TourOption)의 이름과 같은 값이다.
 * API 문서의 설명은 화면 이름을 손으로 옮긴 것이니 화면 이름을 바꾸면 함께 고친다.
 */
@Schema(enumAsRef = true, description = "투어 등급. CLASSIC(클래식), GRAND(그랜드), PREMIUM(프리미엄). "
		+ "허니문 낭만 투어와 효도 힐링 투어는 GRAND와 PREMIUM만 고를 수 있다")
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
