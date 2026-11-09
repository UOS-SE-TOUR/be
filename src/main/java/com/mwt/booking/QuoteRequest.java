package com.mwt.booking;

import java.util.List;

import com.mwt.tour.TourLevel;

import jakarta.validation.constraints.NotNull;

/**
 * 가격을 계산할 고른 내용(설계산출물 5.4 quote). optionIds는 고른 옵션이고, 고르지 않은 종류는 그 등급의 기본 제공으로 계산한다.
 * 인원·테마 규칙과 옵션이 그 상품의 것인지는 Tour.checkApplicable이 확인하고, 여기서는 빈 항목만 거른다.
 */
public record QuoteRequest(
		@NotNull(message = "여행 상품을 골라 주세요.") Long tourId,
		@NotNull(message = "투어 등급을 골라 주세요.") TourLevel level,
		@NotNull(message = "인원을 입력해 주세요.") Integer headcount,
		List<Long> optionIds) {

	List<Long> optionIdsOrEmpty() {
		return optionIds == null ? List.of() : optionIds;
	}

}
