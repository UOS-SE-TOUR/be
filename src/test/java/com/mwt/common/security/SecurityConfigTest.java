package com.mwt.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.ClockConfig;
import com.mwt.member.MemberType;

import jakarta.servlet.http.Cookie;

/**
 * 서버 API 표(설계산출물 4.9)의 "쓸 수 있는 사람"대로 막는지 본다.
 * 실제 Controller가 아직 없어 표의 주소를 흉내 낸 테스트용 Controller를 붙인다. 응답 본문은 로그인한 회원의 ID다.
 */
@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({ SecurityConfig.class, JwtTokenProvider.class, ClockConfig.class, SecurityConfigTest.TestController.class })
class SecurityConfigTest {

	private static final long CUSTOMER_ID = 7;
	private static final long STAFF_ID = 8;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	Clock clock;

	@Value("${jwt.secret-key}")
	String secretKey;

	@Test
	void 누구나_주소는_쿠키_없이_통과() throws Exception {
		mockMvc.perform(post("/api/v1/members/signup")).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/members/login")).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/members/logout")).andExpect(status().isOk());
	}

	@Test
	void 로그아웃은_토큰이_만료되었거나_틀려도_통과() throws Exception {
		mockMvc.perform(post("/api/v1/members/logout").cookie(expiredCustomer())).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/members/logout").cookie(token("not-a-token"))).andExpect(status().isOk());
	}

	@Test
	void 쿠키_없이_고객_주소는_401() throws Exception {
		mockMvc.perform(get("/api/v1/tours")).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/tours/1/levels")).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/bookings/previous")).andExpect(unauthorized());
		mockMvc.perform(post("/api/v1/bookings")).andExpect(unauthorized());
		mockMvc.perform(post("/api/v1/voice/parse")).andExpect(unauthorized());
	}

	@Test
	void 만료되었거나_틀린_토큰이면_401() throws Exception {
		mockMvc.perform(get("/api/v1/tours").cookie(expiredCustomer())).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/tours").cookie(token("not-a-token"))).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/members/me").cookie(expiredCustomer())).andExpect(unauthorized());
	}

	@Test
	void 고객은_고객_주소를_쓰고_로그인한_회원_ID를_받는다() throws Exception {
		mockMvc.perform(get("/api/v1/tours").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
		mockMvc.perform(get("/api/v1/tours/1/levels").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
		mockMvc.perform(get("/api/v1/bookings/previous").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
		mockMvc.perform(post("/api/v1/bookings").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
		mockMvc.perform(post("/api/v1/voice/parse").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
	}

	@Test
	void 고객이_직원_주소를_부르면_403() throws Exception {
		mockMvc.perform(get("/api/v1/staff/items").cookie(customer())).andExpect(forbidden());
		mockMvc.perform(post("/api/v1/staff/tours").cookie(customer())).andExpect(forbidden());
	}

	@Test
	void 쿠키_없이_직원_주소는_401() throws Exception {
		mockMvc.perform(get("/api/v1/staff/items")).andExpect(unauthorized());
		mockMvc.perform(post("/api/v1/staff/tours")).andExpect(unauthorized());
	}

	@Test
	void 직원은_직원_주소를_쓴다() throws Exception {
		mockMvc.perform(get("/api/v1/staff/items").cookie(staff())).andExpect(memberId(STAFF_ID));
		mockMvc.perform(post("/api/v1/staff/tours").cookie(staff())).andExpect(memberId(STAFF_ID));
	}

	@Test
	void 직원이_고객_주소를_부르면_403() throws Exception {
		mockMvc.perform(get("/api/v1/tours").cookie(staff())).andExpect(forbidden());
		mockMvc.perform(post("/api/v1/bookings").cookie(staff())).andExpect(forbidden());
	}

	@Test
	void 내_정보는_로그인한_회원이면_고객이든_직원이든_쓴다() throws Exception {
		mockMvc.perform(get("/api/v1/members/me")).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/members/me").cookie(customer())).andExpect(memberId(CUSTOMER_ID));
		mockMvc.perform(get("/api/v1/members/me").cookie(staff())).andExpect(memberId(STAFF_ID));
	}

	@Test
	void 표에_없는_주소는_Controller가_있어도_막는다() throws Exception {
		mockMvc.perform(get("/api/v1/members/other")).andExpect(unauthorized());
		mockMvc.perform(get("/api/v1/members/other").cookie(customer())).andExpect(forbidden());
		mockMvc.perform(get("/api/v1/members/login")).andExpect(unauthorized());   // 누구나 주소도 POST만 연다
	}

	@Test
	void 같은_이름의_쿠키가_여럿이면_받아들일_수_있는_토큰을_쓴다() throws Exception {
		mockMvc.perform(get("/api/v1/tours").cookie(token("not-a-token"), customer())).andExpect(memberId(CUSTOMER_ID));
	}

	@Test
	void 비동기로_응답해도_로그인한_회원으로_본다() throws Exception {
		MvcResult started = mockMvc.perform(post("/api/v1/voice/async").cookie(customer()))
			.andExpect(request().asyncStarted())
			.andReturn();

		mockMvc.perform(asyncDispatch(started)).andExpect(memberId(CUSTOMER_ID));
	}

	@Test
	void Controller_안에서_난_권한_예외도_500이_아니라_403() throws Exception {
		mockMvc.perform(get("/api/v1/tours/access-denied").cookie(customer())).andExpect(forbidden());
	}

	@Test
	void Controller_안에서_토큰을_못_읽으면_500이_아니라_401() throws Exception {
		mockMvc.perform(get("/api/v1/tours/invalid-token").cookie(customer())).andExpect(unauthorized());
	}

	@Test
	void 비밀번호는_BCrypt로_암호화한다() {
		String encoded = passwordEncoder.encode("password1!");

		assertThat(encoded).startsWith("$2");
		assertThat(passwordEncoder.matches("password1!", encoded)).isTrue();
		assertThat(passwordEncoder.matches("password2!", encoded)).isFalse();
	}

	/**
	 * 암호화는 예외를 던지므로 회원가입 요청 검증에서 막지 않으면 500이 된다(#9에서 비밀번호 길이 규칙으로 막는다).
	 * 비교는 예외 없이 틀렸다고만 하므로 로그인은 "비밀번호가 틀림"으로 끝난다.
	 */
	@Test
	void BCrypt는_72바이트를_넘는_비밀번호를_암호화하지_않는다() {
		String encoded = passwordEncoder.encode("a".repeat(72));
		assertThatThrownBy(() -> passwordEncoder.encode("가".repeat(25)))   // 한글은 한 글자 3바이트
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(passwordEncoder.matches("가".repeat(25), encoded)).isFalse();
	}

	@Test
	void 권한_규칙의_회원_구분은_MemberType_이름과_같다() {
		assertThat(Arrays.stream(MemberType.values()).map(Enum::name))
			.containsExactlyInAnyOrder(MemberTypes.CUSTOMER, MemberTypes.STAFF);
	}

	private Cookie customer() {
		return token(jwtTokenProvider.createToken(CUSTOMER_ID, MemberType.CUSTOMER.name()));
	}

	private Cookie staff() {
		return token(jwtTokenProvider.createToken(STAFF_ID, MemberType.STAFF.name()));
	}

	/** 25시간 전에 만든 고객 토큰. */
	private Cookie expiredCustomer() {
		Clock past = Clock.offset(clock, Duration.ofHours(-25));
		return token(new JwtTokenProvider(new JwtProperties(secretKey), past).createToken(CUSTOMER_ID, "CUSTOMER"));
	}

	private static Cookie token(String value) {
		return new Cookie(JwtTokenProvider.COOKIE_NAME, value);
	}

	private static ResultMatcher unauthorized() {
		return both(status().isUnauthorized(),
				content().json("{\"message\": \"로그인이 필요합니다.\"}", JsonCompareMode.STRICT));
	}

	private static ResultMatcher forbidden() {
		return both(status().isForbidden(), content().json("{\"message\": \"권한이 없습니다.\"}", JsonCompareMode.STRICT));
	}

	private static ResultMatcher memberId(long id) {
		return both(status().isOk(), content().string(Long.toString(id)));
	}

	private static ResultMatcher both(ResultMatcher first, ResultMatcher second) {
		return result -> {
			first.match(result);
			second.match(result);
		};
	}

	@RestController
	static class TestController {

		@PostMapping({ "/api/v1/members/signup", "/api/v1/members/login", "/api/v1/members/logout" })
		String open() {
			return "ok";
		}

		@GetMapping({ "/api/v1/members/me", "/api/v1/tours", "/api/v1/tours/{tourId}/levels", "/api/v1/bookings/previous",
				"/api/v1/staff/items", "/api/v1/members/other", "/api/v1/members/login" })
		Long get(@AuthenticationPrincipal Long memberId) {
			return memberId;
		}

		@PostMapping({ "/api/v1/bookings", "/api/v1/voice/parse", "/api/v1/staff/tours" })
		Long post(@AuthenticationPrincipal Long memberId) {
			return memberId;
		}

		@PostMapping("/api/v1/voice/async")
		Callable<Long> async(@AuthenticationPrincipal Long memberId) {
			return () -> memberId;
		}

		@GetMapping("/api/v1/tours/invalid-token")
		void invalidToken() {
			throw new InvalidTokenException("Controller에서 토큰을 읽다 실패");
		}

		@GetMapping("/api/v1/tours/access-denied")
		void accessDenied() {
			throw new AccessDeniedException("Service에서 막음");
		}

	}

}
