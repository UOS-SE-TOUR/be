package com.mwt.common.security;

/**
 * 로그인 토큰에 담긴 값. memberType은 회원 구분 이름(CUSTOMER, STAFF)이다.
 */
public record TokenClaims(long memberId, String memberType) {
}
