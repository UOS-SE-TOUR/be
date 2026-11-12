package com.mwt.member;

import com.mwt.common.text.TextInput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 입력(설계산출물 5.1 submitSignUp). staffCode는 직원으로 가입할 때만 본다.
 * 글자 수 제한은 member 테이블의 칸 길이다. 비밀번호 길이(바이트)와 직원 코드는 MemberService가 확인한다.
 */
@Schema(description = "회원가입 입력")
public record SignUpRequest(
		@NotBlank(message = "아이디를 입력해 주세요.") @Size(max = 50, message = "아이디는 50자까지 쓸 수 있습니다.") String loginId,
		@Schema(description = "비밀번호. " + MemberService.MIN_PASSWORD_LENGTH + "자 이상, UTF-8로 " + MemberService.MAX_PASSWORD_BYTES
				+ "바이트까지(영문·숫자는 " + MemberService.MAX_PASSWORD_BYTES + "자, 한글은 " + MemberService.MAX_PASSWORD_BYTES / 3
				+ "자)", minLength = MemberService.MIN_PASSWORD_LENGTH)
		@NotBlank(message = "비밀번호를 입력해 주세요.") String password,
		@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50, message = "이름은 50자까지 쓸 수 있습니다.") String name,
		@NotBlank(message = "주소를 입력해 주세요.") @Size(max = 255, message = "주소는 255자까지 쓸 수 있습니다.") String address,
		@Schema(description = "휴대폰 번호(확정 문자를 받는다). 숫자 사이에 하이픈이나 공백이 있어도 되고, 국가 번호(+82)로 시작해도 된다(예: 010-1234-5678, +82 10-1234-5678)")
		@NotBlank(message = "연락처를 입력해 주세요.") @Size(max = 20, message = "연락처는 20자까지 쓸 수 있습니다.") String phone,
		@NotNull(message = "회원 구분을 골라 주세요.") MemberType memberType,
		@Schema(description = "직원 코드. memberType이 STAFF일 때만 보며, 직원이 가입 화면에서 직접 입력한다") String staffCode) {

	/** TextInput으로 다듬고(NFC·보이지 않는 공백까지 앞뒤 공백 떼기) 검증 애너테이션은 다듬은 값을 본다. 비밀번호는 입력한 그대로 둔다. */
	public SignUpRequest {
		loginId = TextInput.normalize(loginId);
		name = TextInput.normalize(name);
		address = TextInput.normalize(address);
		phone = TextInput.normalize(phone);
		staffCode = TextInput.normalize(staffCode);
	}

	@Override
	public String toString() {
		return "SignUpRequest[loginId=" + loginId + ", memberType=" + memberType + "]";   // 비밀번호·직원 코드는 로그에 남기지 않는다
	}

}
