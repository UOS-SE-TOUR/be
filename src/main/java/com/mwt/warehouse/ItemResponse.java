package com.mwt.warehouse;

import com.mwt.tour.Theme;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 창고 물품 하나와 1인당 개수, 현재 수량.
 */
@Schema(description = "창고 물품 하나와 1인당 개수, 현재 수량")
public record ItemResponse(Long id, String name, Theme theme,
		@Schema(description = PER_PERSON) int perPerson,
		@Schema(description = "현재 수량(개). 상품이 확정될 때 모자라도 줄이므로, 모자라면 음수(모자란 만큼)") int quantity) {

	/** 1인당 개수 칸의 설명. 요청 DTO도 같은 설명을 쓴다. */
	static final String PER_PERSON = "1인당 개수(개). 상품이 확정되면 고객 한 명에게 이만큼 주므로 수량이 '인원 × 1인당 개수'만큼 준다. "
			+ "0개부터 100개까지이고, 0이면 고객에게 주지 않는 물품";

	static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getName(), item.getTheme(), item.getPerPerson(), item.getQuantity());
	}

}
