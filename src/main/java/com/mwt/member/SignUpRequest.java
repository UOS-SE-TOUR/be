package com.mwt.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 입력(설계산출물 5.1 submitSignUp). staffCode는 직원으로 가입할 때만 본다.
 * 글자 수 제한은 member 테이블의 칸 길이다. 비밀번호 길이(바이트)와 직원 코드는 MemberService가 확인한다.
 */
public record SignUpRequest(
		@NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 50, message = "아이디는 50자까지 쓸 수 있습니다.") String loginId,
		@NotBlank(message = "비밀번호를 입력해 주세요.") String password,
		@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50, message = "이름은 50자까지 쓸 수 있습니다.") String name,
		@NotBlank(message = "주소를 입력해 주세요.") @Size(max = 255, message = "주소는 255자까지 쓸 수 있습니다.") String address,
		@NotBlank(message = "연락처를 입력해 주세요.") @Size(max = 20, message = "연락처는 20자까지 쓸 수 있습니다.") String phone,
		@NotNull(message = "회원 구분을 골라 주세요.") MemberType memberType,
		String staffCode) {

	/** 앞뒤 공백은 뗀다. 비밀번호는 입력한 그대로 둔다. */
	public SignUpRequest {
		loginId = Inputs.strip(loginId);
		name = Inputs.strip(name);
		address = Inputs.strip(address);
		phone = Inputs.strip(phone);
		staffCode = Inputs.strip(staffCode);
	}

	@Override
	public String toString() {
		return "SignUpRequest[loginId=" + loginId + ", memberType=" + memberType + "]";   // 비밀번호·직원 코드는 로그에 남기지 않는다
	}

}
