package com.mwt.member;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 구분이다(설계산출물 4.2).
 */
@Schema(enumAsRef = true, description = "회원 구분. CUSTOMER(고객), STAFF(직원)")
public enum MemberType {

	CUSTOMER,
	STAFF

}
