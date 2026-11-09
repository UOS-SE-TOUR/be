package com.mwt.common.security;

/**
 * 로그인 토큰을 받아들일 수 없다(서명이 틀림, 만료됨, 형식이 틀림).
 */
public class InvalidTokenException extends RuntimeException {

	InvalidTokenException(String message) {
		super(message);
	}

	InvalidTokenException(String message, Throwable cause) {
		super(message, cause);
	}

}
