package com.mwt.warehouse;

import com.mwt.tour.Theme;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 창고 물품 하나와 현재 수량.
 */
@Schema(description = "창고 물품 하나와 현재 수량")
public record ItemResponse(Long id, String name, Theme theme, @Schema(description = "현재 수량(개)") int quantity) {

	static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getName(), item.getTheme(), item.getQuantity());
	}

}
