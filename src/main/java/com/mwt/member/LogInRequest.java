package com.mwt.member;

import com.mwt.common.text.TextInput;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 입력(설계산출물 5.2 logIn).
 */
public record LogInRequest(
		@NotBlank(message = "아이디를 입력해 주세요.") String loginId,
		@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {

	/** 아이디는 가입할 때처럼 다듬는다. 비밀번호는 입력한 그대로 둔다. */
	public LogInRequest {
		loginId = TextInput.normalize(loginId);
	}

	@Override
	public String toString() {
		return "LogInRequest[loginId=" + loginId + "]";   // 비밀번호는 로그에 남기지 않는다
	}

}
