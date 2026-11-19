package com.mwt.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 있는 창고 물품에 더할 수량. 범위는 Item이 확인한다.
 */
@Schema(description = "있는 창고 물품에 더할 수량")
public record AddQuantityRequest(
		@Schema(description = "더할 수량(개). 1개 이상") @NotNull(message = "수량을 입력해 주세요.") Integer quantity) {

}
