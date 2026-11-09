package com.mwt.warehouse;

import com.mwt.tour.Theme;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 입력한 창고 물품(설계산출물 5.9 addItem). 이름 길이와 수량의 범위는 Item이 확인하고, 여기서는 빈 항목만 거른다.
 */
public record AddItemRequest(
		@NotBlank(message = "물품 이름을 입력해 주세요.") String itemName,
		@NotNull(message = "테마를 골라 주세요.") Theme theme,
		@NotNull(message = "수량을 입력해 주세요.") Integer quantity) {

}
