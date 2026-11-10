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

import com.mwt.OpenApiConfig;
import com.mwt.common.security.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입, 로그인, 내 정보, 로그아웃 요청을 받는다(설계산출물 4.2). 로그인 토큰은 accessToken 쿠키로 주고받는다(4.10).
 */
@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;

	@Operation(summary = "회원가입 (누구나)")
	@ApiResponse(responseCode = "201", description = "가입한 회원의 ID")
	@ApiResponse(responseCode = "400", description = "입력 오류, 이미 쓰는 아이디, 휴대폰 번호가 아닌 연락처, 맞지 않는 직원 코드 등")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
		return new SignUpResponse(memberService.signUp(request));
	}

	/** 토큰을 쿠키로 내려 주고, 화면이 쓸 회원 구분과 이름을 본문으로 돌려준다. */
	@Operation(summary = "로그인 (누구나)",
			description = "로그인 쿠키(accessToken, HttpOnly)를 내려 준다. 화면은 쿠키를 읽을 수 없으니 응답의 회원 구분과 이름으로 로그인 상태를 둔다.")
	@ApiResponse(responseCode = "200", description = "로그인한 회원의 구분과 이름")
	@ApiResponse(responseCode = "400", description = "입력 오류, 아이디나 비밀번호가 틀림")
	@PostMapping("/login")
	public ResponseEntity<MemberResponse> logIn(@Valid @RequestBody LogInRequest request) {
		Member member = memberService.logIn(request.loginId(), request.password());
		String token = jwtTokenProvider.createToken(member.getId(), member.getMemberType().name());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokenCookie(token).maxAge(JwtTokenProvider.VALIDITY).build().toString())
			.body(MemberResponse.from(member));
	}

	/** 새로고침 뒤 화면이 로그인 상태를 되살릴 때 부른다. */
	@Operation(summary = "내 정보 (로그인한 회원)", description = "새로고침 뒤 로그인 상태를 되살릴 때 부른다. 응답은 로그인과 같은 모양이다.")
	@ApiResponse(responseCode = "200", description = "로그인한 회원의 구분과 이름")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@GetMapping("/me")
	public MemberResponse getMe(@AuthenticationPrincipal Long memberId) {
		return MemberResponse.from(memberService.getMember(memberId));
	}

	/**
	 * 쿠키가 HttpOnly라 화면 스크립트로는 지울 수 없어서, 같은 이름과 Path로 값 없이 Max-Age=0인 쿠키를 내려 브라우저가 지우게 한다.
	 * JWT는 서버에 저장하지 않으므로 서버에서 지울 것은 없다. 토큰이 만료된 사람도 부를 수 있다.
	 */
	@Operation(summary = "로그아웃 (누구나)", description = "로그인 쿠키를 지우는 쿠키를 내려 준다. 로그인이 만료됐어도 부를 수 있다.")
	@ApiResponse(responseCode = "204", description = "본문 없음")
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
