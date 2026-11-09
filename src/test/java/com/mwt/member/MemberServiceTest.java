package com.mwt.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.stream.Stream;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mwt.common.exception.BusinessException;

/**
 * DB 없이 보는 MemberService의 규칙(Repository는 가짜). 서버 전체로 보는 규칙은 MemberControllerTest에 있다.
 */
class MemberServiceTest {

	private static final String STAFF_CODE = "0123456789abcdef";
	private static final String PASSWORD = "password1!";

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final MemberService memberService = new MemberService(memberRepository, new BCryptPasswordEncoder(),
			STAFF_CODE);

	@ParameterizedTest(name = "{1}")
	@MethodSource("rejectedSignUps")
	void 가입_규칙에_어긋나면_400이고_저장하지_않는다(SignUpRequest request, String message) {
		assertThatThrownBy(() -> memberService.signUp(request))
			.isInstanceOf(BusinessException.class)
			.hasMessage(message);
		verify(memberRepository, never()).save(any());
	}

	static Stream<Arguments> rejectedSignUps() {
		return Stream.of(
				Arguments.of(signUp("1234567", "010-1234-5678", MemberType.CUSTOMER, null), "비밀번호는 8자 이상으로 입력해 주세요."),
				Arguments.of(signUp("가".repeat(25), "010-1234-5678", MemberType.CUSTOMER, null),
						"비밀번호가 너무 깁니다. 영문·숫자는 72자, 한글은 24자까지 쓸 수 있습니다."),
				Arguments.of(signUp(PASSWORD, "02-123-4567", MemberType.CUSTOMER, null), MemberService.NOT_MOBILE_NUMBER),
				Arguments.of(signUp(PASSWORD, "010-1234-5678-", MemberType.CUSTOMER, null), MemberService.NOT_MOBILE_NUMBER),
				Arguments.of(signUp(PASSWORD, "02-123-4567", MemberType.STAFF, STAFF_CODE), MemberService.NOT_MOBILE_NUMBER),
				Arguments.of(signUp(PASSWORD, "010-1234-5678", MemberType.STAFF, "0123456789abcdeF"),
						"직원 코드가 맞지 않아 직원으로 가입할 수 없습니다."));
	}

	@Test
	void 규칙에_맞으면_비밀번호를_암호화해_저장한다() {
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

		memberService.signUp(signUp(PASSWORD, "+82 10-1234-5678", MemberType.STAFF, STAFF_CODE));

		ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
		verify(memberRepository).save(saved.capture());
		assertThat(saved.getValue().getMemberType()).isEqualTo(MemberType.STAFF);
		assertThat(new BCryptPasswordEncoder().matches(PASSWORD, saved.getValue().getPassword())).isTrue();
	}

	@Test
	void 로그인할_때_72바이트를_넘는_비밀번호는_회원을_찾지_않고_거절한다() {
		assertThatThrownBy(() -> memberService.logIn("hong", "가".repeat(24) + "a"))
			.isInstanceOf(BusinessException.class)
			.hasMessage(MemberService.LOG_IN_FAILED);
		verifyNoInteractions(memberRepository);
	}

	private static SignUpRequest signUp(String password, String phone, MemberType memberType, String staffCode) {
		return new SignUpRequest("hong", password, "홍길동", "서울", phone, memberType, staffCode);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t" })
	void 직원_코드가_비어_있으면_서버가_뜨지_않는다(String staffCode) {
		assertThatThrownBy(() -> new MemberService(null, new BCryptPasswordEncoder(), staffCode))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("STAFF_CODE");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234", "0123456789abcde", " 0123456789abcde ", " 0123456789abcde​", "가나다라마" })   // 마지막은 15바이트
	void 직원_코드가_16바이트보다_짧으면_서버가_뜨지_않는다(String staffCode) {
		assertThatThrownBy(() -> new MemberService(null, new BCryptPasswordEncoder(), staffCode))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("STAFF_CODE가 너무 짧습니다");
	}

	@Test
	void 직원_코드가_16바이트면_뜬다() {
		new MemberService(null, new BCryptPasswordEncoder(), " 0123456789abcdef ");
	}

	@Test
	void 환경변수_직원_코드의_보이지_않는_앞뒤_공백은_코드로_치지_않는다() {
		MemberService service = new MemberService(memberRepository, new BCryptPasswordEncoder(),
				" " + STAFF_CODE + "​");
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.signUp(signUp(PASSWORD, "010-1234-5678", MemberType.STAFF, STAFF_CODE));

		verify(memberRepository).save(any(Member.class));
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
