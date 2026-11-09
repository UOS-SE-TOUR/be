package com.mwt.member;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.security.JwtTokenProvider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입, 로그인, 내 정보, 로그아웃 요청을 받는다(설계산출물 4.2). 로그인 토큰은 accessToken 쿠키로 주고받는다(4.10).
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
		return new SignUpResponse(memberService.signUp(request));
	}

	/** 토큰을 쿠키로 내려 주고, 화면이 쓸 회원 구분과 이름을 본문으로 돌려준다. */
	@PostMapping("/login")
	public ResponseEntity<MemberResponse> logIn(@Valid @RequestBody LogInRequest request) {
		Member member = memberService.logIn(request.loginId(), request.password());
		String token = jwtTokenProvider.createToken(member.getId(), member.getMemberType().name());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokenCookie(token).maxAge(JwtTokenProvider.VALIDITY).build().toString())
			.body(MemberResponse.from(member));
	}

	/** 새로고침 뒤 화면이 로그인 상태를 되살릴 때 부른다. */
	@GetMapping("/me")
	public MemberResponse getMe(@AuthenticationPrincipal Long memberId) {
		return MemberResponse.from(memberService.getMember(memberId));
	}

	/**
	 * 쿠키가 HttpOnly라 화면 스크립트로는 지울 수 없어서, 같은 이름과 Path로 값 없이 Max-Age=0인 쿠키를 내려 브라우저가 지우게 한다.
	 * JWT는 서버에 저장하지 않으므로 서버에서 지울 것은 없다. 토큰이 만료된 사람도 부를 수 있다.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logOut() {
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, tokenCookie("").maxAge(0).build().toString())
			.build();
	}

	/** 로그인과 로그아웃이 같은 속성을 쓴다. SameSite=Lax가 CSRF 토큰을 두지 않는 근거다(4.10). */
	private static ResponseCookie.ResponseCookieBuilder tokenCookie(String value) {
		return ResponseCookie.from(JwtTokenProvider.COOKIE_NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path("/");
	}

}
