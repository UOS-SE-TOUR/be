package com.mwt.tour;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 입력한 여행 상품(설계산출물 5.8 registerTour). 값의 범위와 업무 규칙은 Tour가 확인하고, 여기서는 빈 항목만 거른다.
 */
@Schema(description = "직원이 입력한 여행 상품")
public record RegisterTourRequest(
		@NotNull(message = "테마를 골라 주세요.") Theme theme,
		@Schema(description = "출발일. 오늘보다 뒤") @NotNull(message = "출발일을 입력해 주세요.") LocalDate departureDate,
		@Schema(description = "여행 기간(일). 1일 이상") @NotNull(message = "기간을 입력해 주세요.") Integer duration,
		@Schema(description = "정원(명). 3명 이상이고, 허니문 낭만 투어는 짝수") @NotNull(message = "정원을 입력해 주세요.") Integer capacity,
		@Schema(description = "투어 등급별 1인 가격. 그 테마에서 고를 수 있는 등급마다 하나씩 "
				+ "(허니문 낭만 투어·효도 힐링 투어는 GRAND·PREMIUM, 나머지 테마는 셋 다)")
		@NotEmpty(message = "등급별 가격을 입력해 주세요.")
		List<@NotNull(message = "등급별 가격을 입력해 주세요.") @Valid PriceInput> prices,
		@Schema(description = "교통 선택지. 추가 요금이 0원인 기본 교통이 정확히 하나 있어야 하고, 이름이 겹치면 안 된다")
		@NotEmpty(message = "교통 선택지를 입력해 주세요.")
		List<@NotNull(message = "교통 선택지를 입력해 주세요.") @Valid TransportInput> transports) {

	/** 투어 등급별 1인 가격. */
	@Schema(description = "투어 등급별 1인 가격")
	public record PriceInput(
			@NotNull(message = "투어 등급을 골라 주세요.") TourLevel level,
			@Schema(description = "1인 가격(원)") @NotNull(message = "가격을 입력해 주세요.") Long price) {
	}

	/** 교통 선택지. unitPrice는 기본 교통 대비 1인 추가 요금이고, 기본 교통은 0원이다. */
	@Schema(description = "교통 선택지")
	public record TransportInput(
			@NotBlank(message = "교통 이름을 입력해 주세요.") String name,
			@Schema(description = "기본 교통 대비 1인 추가 요금(원). 기본 교통은 0")
			@NotNull(message = "교통 추가 요금을 입력해 주세요.") Long unitPrice) {
	}

}
