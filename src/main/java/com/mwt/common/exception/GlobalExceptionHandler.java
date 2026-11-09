package com.mwt.common.exception;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller에서 나온 오류를 모두 {"message": "..."}로 돌려준다.
 * 스프링이 던지는 요청 오류(JSON 형식, 필수 값, 없는 주소, 지원하지 않는 메소드 등)는 상위 클래스가 상태 코드를 정하고,
 * 여기서는 본문만 바꾼다. Controller 밖(필터 등)에서 난 오류는 {@link ApiErrorController}가 같은 모양으로 돌려준다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
		String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : messageFor(ex.getStatus());
		return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(message));
	}

	/** 예상하지 못한 오류. 자세한 내용은 서버 로그에만 남기고 화면에는 일반 문구만 보낸다. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
		log.error("처리하지 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ResponseEntity.internalServerError().body(new ErrorResponse(messageFor(HttpStatus.INTERNAL_SERVER_ERROR)));
	}

	/** 요청 본문 검증(@Valid) 실패. 검증 애너테이션의 message를 그대로 돌려준다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, firstError(ex.getBindingResult().getAllErrors()), headers, status, request);
	}

	/** 요청 파라미터·경로 값에 붙인 검증 애너테이션(@Min 등) 실패. */
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, firstError(ex.getAllErrors()), headers, status, request);
	}

	/** 상위 클래스가 처리한 모든 오류가 여기를 지난다. 본문을 ErrorResponse로 바꾼다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		if (statusCode.is5xxServerError()) {
			log.error("요청 처리 중 오류: {}", request.getDescription(false), ex);
		}
		Object errorBody = body instanceof ErrorResponse ? body : new ErrorResponse(messageOf(ex, statusCode));
		return super.handleExceptionInternal(ex, errorBody, headers, statusCode, request);
	}

	/** 상태 코드마다 화면에 보일 일반 문구. */
	static String messageFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "요청 값이 올바르지 않습니다.";
			case 401 -> "로그인이 필요합니다.";
			case 403 -> "권한이 없습니다.";
			case 404 -> "요청한 주소를 찾을 수 없습니다.";
			case 405 -> "지원하지 않는 요청 방식입니다.";
			default -> status.is4xxClientError() ? "요청을 처리할 수 없습니다."
					: "서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.";
		};
	}

	private static String messageOf(Exception ex, HttpStatusCode status) {
		if (ex instanceof MissingServletRequestParameterException e) {
			return "필수 요청 값이 없습니다: " + e.getParameterName();
		}
		if (ex instanceof TypeMismatchException e && e.getPropertyName() != null) {
			return "요청 값의 형식이 올바르지 않습니다: " + e.getPropertyName();
		}
		if (ex instanceof HttpMessageNotReadableException) {
			return "요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요.";
		}
		return messageFor(status);
	}

	/** 검증 오류가 여럿이면 필드 이름 순으로 첫 번째 문구 하나만 보낸다(검증기가 내는 순서는 매번 다르다). */
	private static ErrorResponse firstError(List<? extends MessageSourceResolvable> errors) {
		return errors.stream()
			.sorted(Comparator.comparing((MessageSourceResolvable e) -> e instanceof FieldError f ? f.getField() : ""))
			.map(MessageSourceResolvable::getDefaultMessage)
			.filter(StringUtils::hasText)
			.findFirst()
			.map(ErrorResponse::new)
			.orElseGet(() -> new ErrorResponse(messageFor(HttpStatus.BAD_REQUEST)));
	}

}
