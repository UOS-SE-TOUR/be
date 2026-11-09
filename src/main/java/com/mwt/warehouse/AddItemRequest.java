package com.mwt.warehouse;

import com.mwt.tour.Theme;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 입력한 창고 물품(설계산출물 5.9 addItem). 이름 길이와 수량의 범위는 Item이 확인하고, 여기서는 빈 항목만 거른다.
 * 테마는 새 물품일 때만 필요하다(분석산출물 MWT-UC9 3단계) — 있는 물품에 더할 때는 보내지 않거나 그 물품의 테마를 보낸다.
 */
public record AddItemRequest(
		@NotBlank(message = "물품 이름을 입력해 주세요.") String itemName,
		Theme theme,
		@NotNull(message = "수량을 입력해 주세요.") Integer quantity) {

}
