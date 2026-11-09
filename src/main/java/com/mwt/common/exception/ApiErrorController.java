package com.mwt.common.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller 밖(필터, 서블릿 컨테이너)에서 난 오류도 {"message": "..."}로 돌려준다.
 * 스프링 부트 기본 오류 응답({"timestamp", "status", "error", "path"})을 대신한다.
 * 필터가 BusinessException을 던졌으면 그 상태와 문구를 쓴다.
 */
@RestController
public class ApiErrorController implements ErrorController {

	@RequestMapping("${server.error.path:${error.path:/error}}")
	ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
		BusinessException business = findBusinessException(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
		if (business != null) {
			return GlobalExceptionHandler.toResponse(business);
		}
		HttpStatusCode status = statusOf(request);
		return ResponseEntity.status(status).body(new ErrorResponse(GlobalExceptionHandler.messageFor(status)));
	}

	private static @Nullable BusinessException findBusinessException(@Nullable Object error) {
		for (Throwable t = error instanceof Throwable e ? e : null; t != null; t = t.getCause()) {
			if (t instanceof BusinessException business) {
				return business;
			}
		}
		return null;
	}

	private static HttpStatusCode statusOf(HttpServletRequest request) {
		if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code)) {
			return HttpStatus.NOT_FOUND;   // 오류 없이 /error를 바로 부른 경우
		}
		return code >= 400 && code < 600 ? HttpStatusCode.valueOf(code) : HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
