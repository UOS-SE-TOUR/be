package com.mwt.common.exception;

/**
 * 모든 API 오류 응답의 본문. 화면은 message를 그대로 보여 준다.
 */
public record ErrorResponse(String message) {
}
