package com.mwt.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 업무 규칙 위반. 던지면 {@link GlobalExceptionHandler}가 status와 {"message": message}로 돌려준다.
 * message는 화면에 그대로 보이므로 사용자가 읽을 문구로 쓴다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final HttpStatus status;

	/** 400으로 돌려준다. 입력 오류와 업무 규칙 위반은 대부분 이것을 쓴다. */
	public BusinessException(String message) {
		this(HttpStatus.BAD_REQUEST, message);
	}

	/** 400이 아닌 4xx(404, 409 등)가 필요할 때 쓴다. 5xx는 받지 않는다 — 서버 잘못은 일반 문구로만 알린다. */
	public BusinessException(HttpStatus status, String message) {
		super(message);
		if (!status.is4xxClientError()) {
			throw new IllegalArgumentException("BusinessException은 4xx 상태만 쓴다: " + status);
		}
		this.status = status;
	}

}
