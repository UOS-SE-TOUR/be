package com.mwt.tour;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 입력한 여행 상품(설계산출물 5.8 registerTour). 값의 범위와 업무 규칙은 Tour가 확인하고, 여기서는 빈 항목만 거른다.
 */
public record RegisterTourRequest(
		@NotNull(message = "테마를 골라 주세요.") Theme theme,
		@NotNull(message = "출발일을 입력해 주세요.") LocalDate departureDate,
		@NotNull(message = "기간을 입력해 주세요.") Integer duration,
		@NotNull(message = "정원을 입력해 주세요.") Integer capacity,
		@NotEmpty(message = "등급별 가격을 입력해 주세요.")
		List<@NotNull(message = "등급별 가격을 입력해 주세요.") @Valid PriceInput> prices,
		@NotEmpty(message = "교통 선택지를 입력해 주세요.")
		List<@NotNull(message = "교통 선택지를 입력해 주세요.") @Valid TransportInput> transports) {

	/** 투어 등급별 1인 가격. */
	public record PriceInput(
			@NotNull(message = "투어 등급을 골라 주세요.") TourLevel level,
			@NotNull(message = "가격을 입력해 주세요.") Long price) {
	}

	/** 교통 선택지. unitPrice는 기본 교통 대비 1인 추가 요금이고, 기본 교통은 0원이다. */
	public record TransportInput(
			@NotBlank(message = "교통 이름을 입력해 주세요.") String name,
			@NotNull(message = "교통 추가 요금을 입력해 주세요.") Long unitPrice) {
	}

}
