package com.mwt.member;

import org.springframework.util.StringUtils;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 입력(설계산출물 5.2 logIn).
 */
public record LogInRequest(
		@NotBlank(message = "아이디를 입력해 주세요.") String loginId,
		@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {

	/** 아이디는 가입할 때처럼 앞뒤 공백을 뗀다. */
	public LogInRequest {
		loginId = StringUtils.trimWhitespace(loginId);
	}

	@Override
	public String toString() {
		return "LogInRequest[loginId=" + loginId + "]";   // 비밀번호는 로그에 남기지 않는다
	}

}
