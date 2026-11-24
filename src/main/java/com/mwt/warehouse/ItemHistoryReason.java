package com.mwt.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 창고 물품 수량이 바뀐 이유다.
 */
@Schema(enumAsRef = true, description = "수량이 바뀐 이유. REGISTER(새 물품 등록), ADD(수량 더하기), "
		+ "TAKE_OUT(상품 확정 때 줄임, 확정된 상품에 더 신청했을 때 포함), PUT_BACK(늦은 취소로 되돌림)")
public enum ItemHistoryReason {

	REGISTER,
	ADD,
	TAKE_OUT,
	PUT_BACK

}
