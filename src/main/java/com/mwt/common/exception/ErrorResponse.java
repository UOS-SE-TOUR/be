package com.mwt.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API 오류 응답의 본문. 화면은 message를 그대로 보여 준다.
 */
@Schema(description = "모든 API 오류 응답의 본문")
public record ErrorResponse(@Schema(description = "화면에 그대로 보여 줄 오류 문구") String message) {
}
