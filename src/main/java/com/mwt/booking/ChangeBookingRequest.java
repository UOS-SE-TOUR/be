package com.mwt.booking;

import java.util.List;

import com.mwt.tour.TourLevel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 신청을 바꿀 내용(MWT-UC12). 상품과 인원은 바꿀 수 없어 투어 등급과 옵션만 받는다. 고르지 않은 종류는 그 등급의 기본 제공으로 계산한다.
 * 등급과 옵션이 그 상품에 맞는지는 Tour.checkChangeable이 확인하고, 여기서는 빈 항목만 거른다.
 */
@Schema(description = "신청 바꾸기 가격 계산·신청 바꾸기에 보낼 새 고른 내용")
public record ChangeBookingRequest(
		@NotNull(message = "투어 등급을 골라 주세요.") TourLevel level,
		@Schema(description = "고른 옵션의 ID(옵션 목록의 optionId). 고르지 않은 종류는 그 등급의 기본 제공으로 계산한다. 없으면 빼거나 빈 목록")
		List<Long> optionIds) {

	List<Long> optionIdsOrEmpty() {
		return optionIds == null ? List.of() : optionIds;
	}

}
