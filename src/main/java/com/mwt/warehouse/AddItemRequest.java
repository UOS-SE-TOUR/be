package com.mwt.warehouse;

import com.mwt.tour.Theme;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 입력한 창고 물품(설계산출물 5.9 addItem). 이름 길이와 수량의 범위는 Item이 확인하고, 여기서는 빈 항목만 거른다.
 * 물품은 이름과 테마로 찾으므로 있는 물품에 더할 때도 테마를 보낸다.
 */
@Schema(description = "추가할 창고 물품")
public record AddItemRequest(
		@Schema(description = "물품 이름. 이름과 테마가 같은 물품이 있으면 그 물품에 수량을 더한다")
		@NotBlank(message = "물품 이름을 입력해 주세요.") String itemName,
		@NotNull(message = "테마를 골라 주세요.") Theme theme,
		@Schema(description = "더할 수량(개). 1개 이상") @NotNull(message = "수량을 입력해 주세요.") Integer quantity) {

}
