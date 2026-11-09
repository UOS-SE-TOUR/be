package com.mwt.warehouse;

import com.mwt.tour.Theme;

/**
 * 창고 물품 하나와 현재 수량.
 */
public record ItemResponse(Long id, String name, Theme theme, int quantity) {

	static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getName(), item.getTheme(), item.getQuantity());
	}

}
