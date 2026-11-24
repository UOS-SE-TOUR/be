package com.mwt.tour;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청에 들어가는 옵션 하나(Tour.includedOptions). chosen이 false면 고르지 않아 그 등급이 기본으로 주는 옵션이다.
 */
@Schema(description = "신청에 들어가는 옵션 하나")
public record IncludedOption(OptionCategory category,
		@Schema(description = "옵션 이름(예: 5등급 호텔, 비행기 이코노미석, 커피)") String name,
		@Schema(description = "고객이 고른 옵션이면 true, 고르지 않아 그 등급이 기본으로 주는 옵션이면 false") boolean chosen) {
}
