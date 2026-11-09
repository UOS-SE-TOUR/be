package com.mwt.common.security;

import java.util.Set;

/**
 * 토큰에 담고 권한 규칙이 찾는 회원 구분 이름. member 모듈의 MemberType 이름과 같아야 한다(SecurityConfigTest가 확인한다).
 * 문자열로 다뤄 common.security가 member 모듈을 알지 않게 한다(설계산출물 4.3).
 */
final class MemberTypes {

	static final String CUSTOMER = "CUSTOMER";
	static final String STAFF = "STAFF";

	private static final Set<String> ALL = Set.of(CUSTOMER, STAFF);

	private MemberTypes() {
	}

	static boolean contains(String value) {
		return value != null && ALL.contains(value);   // Set.of(...)는 contains(null)에서 예외를 던진다
	}

}
