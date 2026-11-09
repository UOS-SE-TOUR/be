package com.mwt.common.security;

import org.springframework.security.core.AuthenticationException;

/**
 * 로그인 토큰을 받아들일 수 없다(서명이 틀림, 만료됨, 형식이 틀림).
 * 필터는 이 예외를 잡아 로그인하지 않은 것으로 넘긴다. Spring Security의 로그인 예외라서 Controller에서 새어 나와도
 * 다른 로그인 예외처럼 401 "로그인이 필요합니다."가 된다(메시지는 서버 로그용이라 화면에 보내지 않는다).
 */
public class InvalidTokenException extends AuthenticationException {

	InvalidTokenException(String message) {
		super(message);
	}

	InvalidTokenException(String message, Throwable cause) {
		super(message, cause);
	}

}
