package com.mwt.warehouse;

import java.time.Instant;

import com.mwt.tour.Theme;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "창고 물품 수량 기록 한 줄")
public record ItemHistoryResponse(Long id,
		Long itemId,
		@Schema(description = "그때의 물품 이름") String itemName,
		Theme theme,
		ItemHistoryReason reason,
		@Schema(description = "확정 때 줄임·늦은 취소로 되돌림의 상품 ID. 다른 이유면 null") Long tourId,
		@Schema(description = "그때의 상품 이름. 상품이 없으면 null") String tourTitle,
		@Schema(description = "바뀐 수량. 줄였으면 음수") int changeAmount,
		@Schema(description = "바뀐 뒤 수량(모자라면 음수)") int quantityAfter,
		Instant createdAt) {

	static ItemHistoryResponse from(ItemHistory history) {
		return new ItemHistoryResponse(history.getId(), history.getItemId(), history.getItemName(), history.getTheme(),
				history.getReason(), history.getTourId(), history.getTourTitle(), history.getChangeAmount(),
				history.getQuantityAfter(), history.getCreatedAt());
	}

}
