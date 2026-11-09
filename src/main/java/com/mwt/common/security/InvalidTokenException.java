package com.mwt.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 로그인 토큰을 받아들일 수 없다(서명이 틀림, 만료됨, 형식이 틀림).
 * 필터는 이 예외를 잡아 로그인하지 않은 것으로 넘긴다. Controller에서 새어 나오면 500이 아니라
 * 401 "로그인이 필요합니다."가 된다(메시지는 서버 로그용이라 화면에 보내지 않는다).
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends RuntimeException {

	InvalidTokenException(String message) {
		super(message);
	}

	InvalidTokenException(String message, Throwable cause) {
		super(message, cause);
	}

}
