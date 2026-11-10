package com.mwt.booking;

import java.util.List;

import com.mwt.tour.TourLevel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 가격을 계산하거나 신청할 고른 내용(설계산출물 5.4 quote·applyTour). optionIds는 고른 옵션이고, 고르지 않은 종류는 그 등급의 기본 제공으로 계산한다.
 * 인원·테마 규칙과 옵션이 그 상품의 것인지는 Tour.checkApplicable이 확인하고, 여기서는 빈 항목만 거른다.
 */
@Schema(description = "가격 계산·신청에 보낼 고른 내용")
public record QuoteRequest(
		@Schema(description = "상품 ID(상품 목록의 tourId)") @NotNull(message = "여행 상품을 골라 주세요.") Long tourId,
		@NotNull(message = "투어 등급을 골라 주세요.") TourLevel level,
		@Schema(description = "인원(명). 허니문 낭만 투어는 2명만") @NotNull(message = "인원을 입력해 주세요.") Integer headcount,
		@Schema(description = "고른 옵션의 ID(옵션 목록의 optionId). 고르지 않은 종류는 그 등급의 기본 제공으로 계산한다. 없으면 빼거나 빈 목록")
		List<Long> optionIds) {

	List<Long> optionIdsOrEmpty() {
		return optionIds == null ? List.of() : optionIds;
	}

}
