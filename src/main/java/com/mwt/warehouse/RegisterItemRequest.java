package com.mwt.warehouse;

import com.mwt.tour.Theme;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 직원이 등록하는 새 창고 물품. 이름 길이와 1인당 개수·수량의 범위는 Item이 확인하고, 여기서는 빈 항목만 거른다.
 */
@Schema(description = "등록할 새 창고 물품")
public record RegisterItemRequest(
		@Schema(description = "물품 이름. 이름과 테마가 같은 물품이 이미 있으면 등록하지 않는다")
		@NotBlank(message = "물품 이름을 입력해 주세요.") String itemName,
		@NotNull(message = "테마를 골라 주세요.") Theme theme,
		@Schema(description = ItemResponse.PER_PERSON) @NotNull(message = "1인당 개수를 입력해 주세요.") Integer perPerson,
		@Schema(description = "처음 수량(개). 1개 이상") @NotNull(message = "수량을 입력해 주세요.") Integer quantity) {

}
