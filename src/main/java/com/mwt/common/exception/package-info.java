/**
 * 공통 오류 응답. 모든 API 오류는 상태 코드와 {"message": "..."} 하나로 돌려준다(설계산출물 v0.4 4.9 마지막 문단).
 *
 * <p>각 모듈은 업무 규칙을 어기면 {@link com.mwt.common.exception.BusinessException}을 던지기만 하고, 응답 모양은
 * {@link com.mwt.common.exception.GlobalExceptionHandler}와 {@link com.mwt.common.exception.ApiErrorController}가 정한다.
 */
package com.mwt.common.exception;
