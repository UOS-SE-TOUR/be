package com.mwt.tour;

import java.util.List;

import com.mwt.OpenApiConfig;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 상품에서 고를 수 있는 투어 등급과 등급별 1인 가격·제공 내용(설계산출물 5.4 getLevels).
 * service·souvenir·vehicle은 등급과 상관없이 그 테마가 제공하는 테마 서비스·기념품·현지 차량이다(분석산출물 1.4 표 2).
 */
@Schema(description = "상품의 투어 등급과 등급별 1인 가격·제공 내용")
public record TourLevelsResponse(@Schema(description = "테마 서비스(등급과 상관없이 그 테마가 주는 것)") String service,
		@Schema(description = "테마 기념품(등급과 상관없음)") String souvenir,
		@Schema(description = "현지 차량(등급과 상관없음)") String vehicle,
		@Schema(description = "고를 수 있는 투어 등급. 클래식, 그랜드, 프리미엄 순") List<Level> levels) {

	/** 투어 등급 하나. hotelGrade·meal·includesChampagne은 그 등급이 기본으로 주는 호텔 등급·식사·샴페인이다. */
	@Schema(name = "LevelDetail", description = "투어 등급 하나와 그 등급의 1인 가격·기본 제공")
	public record Level(TourLevel level,
			@Schema(description = OpenApiConfig.LEVEL_NAME) String name,
			@Schema(description = "1인 가격(원)") long price,
			@Schema(description = "기본 호텔 등급(4면 4등급 호텔)") int hotelGrade,
			@Schema(description = "기본 식사(식사 옵션의 이름과 같다)") String meal,
			@Schema(description = "샴페인을 기본으로 주는지") boolean includesChampagne) {
	}

}
