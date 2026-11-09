package com.mwt.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * DB 없이 보는 MemberService의 규칙. API로 보는 규칙은 MemberControllerTest에 있다.
 */
class MemberServiceTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t" })
	void 직원_코드가_비어_있으면_서버가_뜨지_않는다(String staffCode) {
		assertThatThrownBy(() -> new MemberService(null, new BCryptPasswordEncoder(), staffCode))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("STAFF_CODE");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234", "0123456789abcde", " 0123456789abcde ", "가나다라마" })   // 마지막은 15바이트
	void 직원_코드가_16바이트보다_짧으면_서버가_뜨지_않는다(String staffCode) {
		assertThatThrownBy(() -> new MemberService(null, new BCryptPasswordEncoder(), staffCode))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("STAFF_CODE가 너무 짧습니다");
	}

	@Test
	void 직원_코드가_16바이트면_뜬다() {
		new MemberService(null, new BCryptPasswordEncoder(), " 0123456789abcdef ");
	}

	@ParameterizedTest
	@ValueSource(strings = { "uk_member_login_id", "member.uk_member_login_id", "MEMBER.UK_MEMBER_LOGIN_ID" })
	void 아이디_유일_키_위반이면_아이디_중복으로_본다(String constraintName) {
		assertThat(MemberService.isLoginIdTaken(violation(constraintName))).isTrue();
	}

	@Test
	void 다른_무결성_오류는_아이디_중복으로_보지_않는다() {
		assertThat(MemberService.isLoginIdTaken(violation("fk_booking_member"))).isFalse();
		assertThat(MemberService.isLoginIdTaken(violation(null))).isFalse();
		assertThat(MemberService.isLoginIdTaken(new DataIntegrityViolationException("Data too long"))).isFalse();
	}

	@Test
	void 요청을_로그에_찍어도_비밀번호와_직원_코드는_나오지_않는다() {
		SignUpRequest signUp = new SignUpRequest("hong", "secret-password", "홍길동", "서울", "010-1234-5678",
				MemberType.STAFF, "secret-code");
		LogInRequest logIn = new LogInRequest("hong", "secret-password");

		assertThat(signUp.toString()).contains("hong").doesNotContain("secret");
		assertThat(logIn.toString()).contains("hong").doesNotContain("secret");
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException("Duplicate entry",
				new SQLException("Duplicate entry", "23000", 1062), constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
