package com.mwt.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mwt.common.exception.BusinessException;
import com.mwt.common.exception.GlobalExceptionHandler;
import com.mwt.common.text.TextInput;
import com.mwt.notification.SmsSender;

/**
 * 회원가입과 로그인을 처리하는 member 모듈의 공개 Service다(설계산출물 4.2). 다른 모듈은 회원을 getMember로 가져간다.
 */
@Service
public class MemberService {

	/** BCrypt는 72바이트를 넘는 비밀번호를 암호화하지 않고 예외를 던진다. */
	static final int MAX_PASSWORD_BYTES = 72;

	static final int MIN_PASSWORD_LENGTH = 8;

	static final int MIN_STAFF_CODE_BYTES = 16;

	/** 연락처의 꼴. 숫자와, 숫자 사이의 하이픈·공백과, 맨 앞의 +만 받는다. +는 국가 번호 82 앞에만 쓸 수 있다(+82 10-…). */
	private static final Pattern PHONE_CHARACTERS = Pattern.compile("(?:\\+(?=82))?[0-9]+(?:[ -]+[0-9]+)*");

	static final String LOGIN_ID_TAKEN = "이미 쓰는 아이디입니다.";

	static final String LOG_IN_FAILED = "아이디 또는 비밀번호가 맞지 않습니다.";

	static final String NOT_MOBILE_NUMBER = "연락처는 휴대폰 번호로 입력해 주세요(예: 010-1234-5678).";

	private static final String LOGIN_ID_UNIQUE_KEY = "uk_member_login_id";   // V1__init.sql

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final byte[] staffCode;

	MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
			@Value("${member.staff-code:}") String staffCode) {
		// .env 값 끝의 공백까지 코드로 치지 않는다. 입력(SignUpRequest)도 같은 방법(TextInput)으로 다듬는다
		String normalized = TextInput.normalize(staffCode);
		if (normalized == null || normalized.isEmpty()) {
			throw new IllegalStateException("환경변수 STAFF_CODE가 비어 있습니다. README 환경변수 절을 보고 넣어 주세요.");
		}
		byte[] code = normalized.getBytes(StandardCharsets.UTF_8);
		if (code.length < MIN_STAFF_CODE_BYTES) {   // 가입 API에 시도 횟수 제한이 없어 짧은 코드는 하나씩 넣어 보면 맞힌다
			throw new IllegalStateException("STAFF_CODE가 너무 짧습니다(" + code.length + "바이트). " + MIN_STAFF_CODE_BYTES
					+ "바이트 이상으로 넣어 주세요(예: openssl rand -hex 8).");
		}
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.staffCode = code;
	}

	/**
	 * 회원가입(MWT-UC1). 직원은 직원 코드가 맞아야 가입한다. 비밀번호는 8자 이상 72바이트 이하, 연락처는 고객·직원 모두
	 * 휴대폰 번호다. 연락처는 문자를 보낼 때와 같은 꼴(숫자만 남긴 국내 번호, 예: 01012345678)로 바꿔 저장한다.
	 * 가입한 회원 ID를 돌려준다.
	 */
	@Transactional
	public Long signUp(SignUpRequest request) {
		if (request.memberType() == MemberType.STAFF && !isStaffCode(request.staffCode())) {
			throw new BusinessException("직원 코드가 맞지 않아 직원으로 가입할 수 없습니다.");
		}
		if (TextInput.length(request.password()) < MIN_PASSWORD_LENGTH) {
			throw new BusinessException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상으로 입력해 주세요.");
		}
		if (isTooLong(request.password())) {
			throw new BusinessException("비밀번호가 너무 깁니다. 영문·숫자는 72자, 한글은 24자까지 쓸 수 있습니다.");
		}
		// 확정 문자를 받을 수 있는 번호만 받는다. 휴대폰 번호 꼴이 아니면 문자가 조용히 빠지므로(notification) 가입 때 알린다
		if (!PHONE_CHARACTERS.matcher(request.phone()).matches() || !SmsSender.isMobileNumber(request.phone())) {
			throw new BusinessException(NOT_MOBILE_NUMBER);
		}
		if (memberRepository.existsByLoginId(request.loginId())) {
			throw new BusinessException(LOGIN_ID_TAKEN);
		}
		Member member = new Member(request.loginId(), passwordEncoder.encode(request.password()), request.name(),
				request.address(), SmsSender.normalizeNumber(request.phone()), request.memberType());
		try {
			return memberRepository.save(member).getId();
		} catch (DataIntegrityViolationException e) {
			if (isLoginIdTaken(e)) {   // 같은 아이디로 동시에 가입해 둘 다 위 확인을 지났다
				throw new BusinessException(LOGIN_ID_TAKEN);
			}
			throw e;
		}
	}

	/**
	 * 로그인(MWT-UC2). 아이디와 비밀번호가 맞으면 회원을 돌려준다. 토큰은 MemberController가 만든다.
	 * BCrypt 비교는 72바이트 뒤를 보지 않아 72바이트 비밀번호 뒤에 아무 글자나 붙여도 맞다고 하므로, 그런 입력은 먼저 틀렸다고 한다.
	 */
	@Transactional(readOnly = true)
	public Member logIn(String loginId, String password) {
		if (isTooLong(password)) {
			throw new BusinessException(LOG_IN_FAILED);
		}
		return memberRepository.findByLoginId(loginId)
			.filter(member -> passwordEncoder.matches(password, member.getPassword()))
			.orElseThrow(() -> new BusinessException(LOG_IN_FAILED));
	}

	/**
	 * 로그인한 회원을 가져온다. memberId는 로그인 토큰에 담긴 회원 ID다.
	 * 회원이 없으면(토큰을 받은 뒤 DB를 비운 경우 등) 다시 로그인하게 401을 돌려준다.
	 */
	@Transactional(readOnly = true)
	public Member getMember(long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED,
					GlobalExceptionHandler.messageFor(HttpStatus.UNAUTHORIZED)));
	}

	private static boolean isTooLong(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

	/** 길이가 같으면 걸리는 시간이 같게 비교한다. */
	private boolean isStaffCode(String input) {
		return input != null && MessageDigest.isEqual(input.getBytes(StandardCharsets.UTF_8), staffCode);
	}

	/** 아이디 유일 키(uk_member_login_id) 위반인지. 다른 무결성 오류는 서버 잘못이라 그대로 던진다. */
	static boolean isLoginIdTaken(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
				return violation.getConstraintName().toLowerCase(Locale.ROOT).endsWith(LOGIN_ID_UNIQUE_KEY);
			}
		}
		return false;
	}

}
