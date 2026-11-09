package com.mwt.common.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller에서 나온 오류를 모두 {"message": "..."}로 돌려준다.
 * 스프링이 던지는 요청 오류(JSON 형식, 필수 값, 없는 주소, 지원하지 않는 메소드 등)는 상위 클래스가 상태 코드를 정하고,
 * 여기서는 본문만 바꾼다. Controller 밖(필터 등)에서 난 오류는 {@link ApiErrorController}가 같은 모양으로 돌려준다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String TYPE_MISMATCH = "요청 값의 형식이 올바르지 않습니다: ";

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
		return toResponse(ex);
	}

	/**
	 * Controller 클래스에 @Validated를 붙여 인자를 검증했을 때의 실패는 400이다.
	 * 같은 예외가 엔티티 저장·Service 검증·Controller 반환값 검증에서 나면 서버 잘못이므로 500으로 돌리고 로그에 남긴다.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) throws Exception {
		Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
		if (violations == null || violations.isEmpty()
				|| !violations.stream().allMatch(GlobalExceptionHandler::isControllerArgument)) {
			return handleUnexpectedException(ex, request);
		}
		return ResponseEntity.badRequest().body(firstError(violations.stream()
			.map(v -> new ValidationError(v.getPropertyPath().toString(), v.getMessage()))));
	}

	/**
	 * 예상하지 못한 오류. 자세한 내용은 서버 로그에만 남기고 화면에는 일반 문구만 보낸다.
	 * 4xx를 정한 @ResponseStatus 예외는 삼키지 않고 다시 던져 스프링이 그 상태로 돌려주게 한다(본문은 ApiErrorController).
	 * 로그인·권한 예외(Spring Security)도 다시 던져 Security가 401·403으로 돌려주게 한다(common.security의 SecurityConfig).
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) throws Exception {
		if (ex instanceof AuthenticationException || ex instanceof AccessDeniedException) {
			throw ex;
		}
		ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
		if (responseStatus != null && responseStatus.code().is4xxClientError()) {
			throw ex;
		}
		log.error("처리하지 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ResponseEntity.internalServerError().body(new ErrorResponse(messageFor(HttpStatus.INTERNAL_SERVER_ERROR)));
	}

	/** 요청 본문(@Valid)·쿼리 객체 검증과 바인딩 실패. 검증 애너테이션의 message를 그대로 돌려준다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Stream<ValidationError> errors = ex.getBindingResult().getAllErrors().stream()
			.map(e -> validationError(e, null));
		return handleExceptionInternal(ex, firstError(errors), headers, status, request);
	}

	/** 요청 파라미터·경로 값에 붙인 검증 애너테이션(@Min 등) 실패. 필드 이름 대신 파라미터 이름으로 정렬한다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Stream<ValidationError> parameterErrors = ex.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(e -> validationError(e, result.getMethodParameter().getParameterName())));
		Stream<ValidationError> crossParameterErrors = ex.getCrossParameterValidationResults().stream()
			.map(e -> validationError(e, null));
		return handleExceptionInternal(ex, firstError(Stream.concat(parameterErrors, crossParameterErrors)), headers,
				status, request);
	}

	/** 상위 클래스가 처리한 모든 오류가 여기를 지난다. 본문을 ErrorResponse로 바꾸고, 5xx는 일반 문구만 보낸다. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		Object errorBody;
		if (statusCode.is5xxServerError()) {
			log.error("요청 처리 중 오류: {}", request.getDescription(false), ex);
			errorBody = new ErrorResponse(messageFor(statusCode));
		} else {
			errorBody = body instanceof ErrorResponse ? body : new ErrorResponse(messageOf(ex, statusCode));
		}
		return super.handleExceptionInternal(ex, errorBody, headers, statusCode, request);
	}

	static ResponseEntity<ErrorResponse> toResponse(BusinessException ex) {
		String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : messageFor(ex.getStatus());
		return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(message));
	}

	/** 상태 코드마다 화면에 보일 일반 문구. 401·403 응답을 직접 쓰는 common.security도 이 문구를 쓴다. */
	public static String messageFor(HttpStatusCode status) {
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
			return TYPE_MISMATCH + e.getPropertyName();
		}
		if (ex instanceof HttpMessageNotReadableException) {
			return "요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요.";
		}
		return messageFor(status);
	}

	/** 검증 대상이 Controller이고 위반 자리가 메소드 인자일 때만 요청 잘못이다(반환값 위반은 서버 잘못). */
	private static boolean isControllerArgument(ConstraintViolation<?> violation) {
		if (!AnnotatedElementUtils.hasAnnotation(violation.getRootBeanClass(), Controller.class)) {
			return false;
		}
		for (Path.Node node : violation.getPropertyPath()) {
			if (node.getKind() == ElementKind.PARAMETER || node.getKind() == ElementKind.CROSS_PARAMETER) {
				return true;
			}
		}
		return false;
	}

	/** 필드 오류는 그 필드 이름으로, 아니면 받은 이름(파라미터 이름, 없으면 null)으로 정렬 기준을 잡는다. */
	private static ValidationError validationError(MessageSourceResolvable error, @Nullable String name) {
		if (error instanceof FieldError f) {
			// 바인딩 실패(숫자 자리에 글자 등)의 기본 문구는 스프링의 영어 설명이라 화면용 문구로 바꾼다
			return new ValidationError(f.getField(), f.isBindingFailure() ? TYPE_MISMATCH + f.getField()
					: f.getDefaultMessage());
		}
		return new ValidationError(name, error.getDefaultMessage());
	}

	/**
	 * 검증 오류가 여럿이면 문구 하나만 보낸다. 필드(파라미터) 이름 순이고, 이름이 없는 오류(클래스 단위 검증)는 뒤에 둔다.
	 * 검증기가 내는 순서는 매번 달라 같은 이름 안에서는 문구로 정렬한다.
	 */
	private static ErrorResponse firstError(Stream<ValidationError> errors) {
		return errors.filter(e -> StringUtils.hasText(e.message()))
			.sorted(Comparator.comparing(ValidationError::name, Comparator.nullsLast(Comparator.<String>naturalOrder()))
				.thenComparing(ValidationError::message))
			.findFirst()
			.map(e -> new ErrorResponse(e.message()))
			.orElseGet(() -> new ErrorResponse(messageFor(HttpStatus.BAD_REQUEST)));
	}

	private record ValidationError(@Nullable String name, @Nullable String message) {
	}

}
