package com.mwt.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 창고 물품의 새 1인당 개수. 범위는 Item이 확인한다.
 */
@Schema(description = "창고 물품의 새 1인당 개수")
public record ChangePerPersonRequest(
		@Schema(description = ItemResponse.PER_PERSON) @NotNull(message = "1인당 개수를 입력해 주세요.") Integer perPerson) {

}
