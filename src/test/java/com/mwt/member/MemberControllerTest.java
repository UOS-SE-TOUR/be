package com.mwt.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.mwt.TestcontainersConfiguration;
import com.mwt.common.security.JwtTokenProvider;
import com.mwt.common.security.TokenClaims;

import jakarta.servlet.http.Cookie;

/**
 * 회원가입·로그인·내 정보·로그아웃 API(설계산출물 5.1, 5.2, 5.10). 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다.
 * 직원 코드는 테스트 설정(src/test/resources/config/application.yml)의 값이다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerTest {

	private static final String SIGN_UP = "/api/v1/members/signup";
	private static final String LOG_IN = "/api/v1/members/login";
	private static final String ME = "/api/v1/members/me";
	private static final String LOG_OUT = "/api/v1/members/logout";
	private static final String STAFF_CODE = "test-only-staff-code";
	private static final String PASSWORD = "password1!";

	@Autowired
	MockMvc mockMvc;

	/** 동시 가입 테스트에서 아이디 중복 확인만 바꾼다. 나머지는 실제 Repository대로 돈다. */
	@MockitoSpyBean
	MemberRepository memberRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@AfterEach
	void deleteMembers() {
		memberRepository.deleteAll();
	}

	@Test
	void 고객으로_가입하면_201과_회원_ID를_돌려주고_비밀번호는_암호화해_저장한다() throws Exception {
		String response = signUp(customer("hong"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.memberId").isNumber())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long memberId = JsonPath.<Number>read(response, "$.memberId").longValue();

		Member member = memberRepository.findById(memberId).orElseThrow();
		assertThat(member.getLoginId()).isEqualTo("hong");
		assertThat(member.getName()).isEqualTo("홍길동");
		assertThat(member.getAddress()).isEqualTo("서울시 동대문구 서울시립대로 163");
		assertThat(member.getPhone()).isEqualTo("010-1234-5678");
		assertThat(member.getMemberType()).isEqualTo(MemberType.CUSTOMER);
		assertThat(member.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, member.getPassword())).isTrue();
	}

	@Test
	void 입력의_앞뒤_공백은_떼고_저장한다() throws Exception {
		signUp(new Body(" hong ", PASSWORD, " 홍길동 ", " 서울 ", " 010-1234-5678 ", "CUSTOMER", null))
			.andExpect(status().isCreated());

		Member member = memberRepository.findByLoginId("hong").orElseThrow();
		assertThat(member).extracting(Member::getName, Member::getAddress, Member::getPhone)
			.containsExactly("홍길동", "서울", "010-1234-5678");
		logIn(" hong ", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 직원_코드가_맞으면_직원으로_가입한다() throws Exception {
		signUp(staff("kim", " " + STAFF_CODE + " ")).andExpect(status().isCreated());

		assertThat(memberRepository.findByLoginId("kim").orElseThrow().getMemberType()).isEqualTo(MemberType.STAFF);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "wrong-code", "TEST-ONLY-STAFF-CODE", "test-only-staff-code2", "test-only-staff-cod" })
	void 직원_코드가_틀리거나_없으면_400이고_저장하지_않는다(String staffCode) throws Exception {
		signUp(staff("kim", staffCode))
			.andExpect(badRequest("직원 코드가 맞지 않아 직원으로 가입할 수 없습니다."));

		assertThat(memberRepository.count()).isZero();
	}

	@Test
	void 고객으로_가입하면_직원_코드를_보내도_고객이다() throws Exception {
		signUp(customer("hong").withStaffCode(STAFF_CODE)).andExpect(status().isCreated());

		assertThat(memberRepository.findByLoginId("hong").orElseThrow().getMemberType()).isEqualTo(MemberType.CUSTOMER);
	}

	@ParameterizedTest
	@ValueSource(strings = { "hong", "HONG", " Hong", "hóng", "ｈｏｎｇ" })
	void 이미_있는_아이디로_가입하면_400(String loginId) throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());

		signUp(customer(loginId)).andExpect(badRequest(MemberService.LOGIN_ID_TAKEN));   // MemberRepository의 정렬 규칙 설명
		assertThat(memberRepository.count()).isOne();
		logIn(loginId, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 같은_아이디로_동시에_가입해_중복_확인을_지나도_500이_아니라_400() throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());
		doReturn(false).when(memberRepository).existsByLoginId("hong");   // 다른 가입이 확인과 저장 사이에 끼어든 것과 같다

		signUp(customer("hong")).andExpect(badRequest(MemberService.LOGIN_ID_TAKEN));
		verify(memberRepository, times(2)).save(any(Member.class));   // 중복 확인이 아니라 DB의 유일 키가 막았다
		assertThat(memberRepository.count()).isOne();
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("invalidSignUps")
	void 가입_항목이_비었거나_너무_길면_400이고_저장하지_않는다(Body body, String message) throws Exception {
		signUp(body).andExpect(badRequest(message));

		assertThat(memberRepository.count()).isZero();
	}

	static Stream<Arguments> invalidSignUps() {
		Body ok = customer("hong");
		return Stream.of(
				Arguments.of(ok.withLoginId(null), "아이디를 입력해 주세요."),
				Arguments.of(ok.withLoginId("  "), "아이디를 입력해 주세요."),
				Arguments.of(ok.withPassword(null), "비밀번호를 입력해 주세요."),
				Arguments.of(ok.withPassword(" "), "비밀번호를 입력해 주세요."),
				Arguments.of(ok.withName(""), "이름을 입력해 주세요."),
				Arguments.of(ok.withAddress(null), "주소를 입력해 주세요."),
				Arguments.of(ok.withPhone(" "), "연락처를 입력해 주세요."),
				Arguments.of(ok.withMemberType(null), "회원 구분을 골라 주세요."),
				Arguments.of(ok.withLoginId("a".repeat(51)), "아이디는 50자까지 쓸 수 있습니다."),
				Arguments.of(ok.withName("가".repeat(51)), "이름은 50자까지 쓸 수 있습니다."),
				Arguments.of(ok.withAddress("가".repeat(256)), "주소는 255자까지 쓸 수 있습니다."),
				Arguments.of(ok.withPhone("0".repeat(21)), "연락처는 20자까지 쓸 수 있습니다."));
	}

	@Test
	void 칸_길이를_꽉_채워도_가입한다() throws Exception {
		signUp(new Body("a".repeat(50), PASSWORD, "가".repeat(50), "가".repeat(255), "+82 10 - 1234 - 5678", "CUSTOMER",
				null))
			.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "01012345678", "010 1234 5678", "+82 10-1234-5678", "+82 010-1234-5678", "011-123-4567" })
	void 휴대폰_번호면_하이픈_공백_국가_번호와_함께_받는다(String phone) throws Exception {
		signUp(customer("hong").withPhone(phone)).andExpect(status().isCreated());

		assertThat(memberRepository.findByLoginId("hong").orElseThrow().getPhone()).isEqualTo(phone);   // 입력한 그대로 둔다
	}

	@ParameterizedTest
	@ValueSource(strings = { "02-123-4567", "abc", "010-1234-567", "010-1234-56789", "010.1234.5678", "010-abcd-1234-5678",
			"(010) 1234-5678", "010-1234-5678+" })
	void 연락처가_휴대폰_번호가_아니면_400이고_저장하지_않는다(String phone) throws Exception {
		signUp(customer("hong").withPhone(phone)).andExpect(badRequest("연락처는 휴대폰 번호로 입력해 주세요(예: 010-1234-5678)."));

		assertThat(memberRepository.count()).isZero();
	}

	@Test
	void 직원도_연락처는_휴대폰_번호여야_한다() throws Exception {
		signUp(staff("kim", STAFF_CODE).withPhone("02-123-4567"))
			.andExpect(badRequest("연락처는 휴대폰 번호로 입력해 주세요(예: 010-1234-5678)."));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234567", "가나다라마바사", "a b c d" })
	void 비밀번호가_8자보다_짧으면_400이고_저장하지_않는다(String password) throws Exception {
		signUp(customer("hong").withPassword(password)).andExpect(badRequest("비밀번호는 8자 이상으로 입력해 주세요."));

		assertThat(memberRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345678", "가나다라마바사아" })
	void 비밀번호가_8자면_가입한다(String password) throws Exception {
		signUp(customer("hong").withPassword(password)).andExpect(status().isCreated());

		logIn("hong", password).andExpect(status().isOk());
	}

	/** BCrypt가 72바이트까지만 받으므로 글자 수가 아니라 UTF-8 바이트로 센다(한글은 한 글자 3바이트). */
	@ParameterizedTest
	@MethodSource("tooLongPasswords")
	void 비밀번호가_72바이트를_넘으면_500이_아니라_400(String password) throws Exception {
		signUp(customer("hong").withPassword(password))
			.andExpect(badRequest("비밀번호가 너무 깁니다. 영문·숫자는 72자, 한글은 24자까지 쓸 수 있습니다."));

		assertThat(memberRepository.count()).isZero();
	}

	static Stream<String> tooLongPasswords() {
		return Stream.of("a".repeat(73), "가".repeat(25), "a".repeat(70) + "가");
	}

	@Test
	void 비밀번호가_72바이트면_가입하고_로그인한다() throws Exception {
		String password = "가".repeat(24);
		signUp(customer("hong").withPassword(password)).andExpect(status().isCreated());

		logIn("hong", password).andExpect(status().isOk());
	}

	/** BCrypt 비교는 72바이트 뒤를 보지 않는다(SecurityConfigTest). 서버가 막지 않으면 이 로그인이 성공한다. */
	@ParameterizedTest
	@ValueSource(strings = { "a", "가", "password1!" })
	void 비밀번호_72바이트_뒤에_글자를_붙이면_로그인하지_않는다(String extra) throws Exception {
		String password = "가".repeat(24);
		signUp(customer("hong").withPassword(password)).andExpect(status().isCreated());

		logIn("hong", password + extra)
			.andExpect(badRequest("아이디 또는 비밀번호가 맞지 않습니다."))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void 로그인하면_accessToken_쿠키를_내려_주고_회원_구분과_이름을_돌려준다() throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());
		long memberId = memberRepository.findByLoginId("hong").orElseThrow().getId();

		Cookie token = logIn("hong", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(content().json("{\"memberType\": \"CUSTOMER\", \"name\": \"홍길동\"}", JsonCompareMode.STRICT))
			.andExpect(cookie().httpOnly(JwtTokenProvider.COOKIE_NAME, true))
			.andExpect(cookie().secure(JwtTokenProvider.COOKIE_NAME, true))
			.andExpect(cookie().sameSite(JwtTokenProvider.COOKIE_NAME, "Lax"))
			.andExpect(cookie().path(JwtTokenProvider.COOKIE_NAME, "/"))
			.andExpect(cookie().maxAge(JwtTokenProvider.COOKIE_NAME, 86400))
			.andReturn()
			.getResponse()
			.getCookie(JwtTokenProvider.COOKIE_NAME);

		assertThat(jwtTokenProvider.parseToken(token.getValue())).isEqualTo(new TokenClaims(memberId, "CUSTOMER"));
	}

	@Test
	void 직원이_로그인하면_토큰과_응답에_직원으로_담는다() throws Exception {
		signUp(staff("kim", STAFF_CODE)).andExpect(status().isCreated());

		Cookie token = logIn("kim", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberType").value("STAFF"))
			.andReturn()
			.getResponse()
			.getCookie(JwtTokenProvider.COOKIE_NAME);

		assertThat(jwtTokenProvider.parseToken(token.getValue()).memberType()).isEqualTo("STAFF");
	}

	@ParameterizedTest
	@MethodSource("wrongLogIns")
	void 아이디나_비밀번호가_틀리면_400이고_쿠키를_주지_않는다(String loginId, String password) throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());

		logIn(loginId, password)
			.andExpect(badRequest("아이디 또는 비밀번호가 맞지 않습니다."))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	static Stream<Arguments> wrongLogIns() {
		return Stream.of(
				Arguments.of("hong", "password2!"),
				Arguments.of("nobody", PASSWORD),
				Arguments.of("hong", PASSWORD + " "),
				Arguments.of("hong", "가".repeat(25)));
	}

	@Test
	void 로그인_항목이_비면_400() throws Exception {
		logIn(" ", PASSWORD).andExpect(badRequest("아이디를 입력해 주세요."));
		logIn("hong", null).andExpect(badRequest("비밀번호를 입력해 주세요."));
	}

	@Test
	void 내_정보는_로그인한_회원의_구분과_이름을_돌려준다() throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());
		signUp(staff("kim", STAFF_CODE).withName("김직원")).andExpect(status().isCreated());

		mockMvc.perform(get(ME).cookie(logInCookie("hong")))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"memberType\": \"CUSTOMER\", \"name\": \"홍길동\"}", JsonCompareMode.STRICT));
		mockMvc.perform(get(ME).cookie(logInCookie("kim")))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"memberType\": \"STAFF\", \"name\": \"김직원\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 내_정보는_로그인하지_않았거나_회원이_없으면_401() throws Exception {
		mockMvc.perform(get(ME)).andExpect(unauthorized());

		Cookie missingMember = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(999_999, "CUSTOMER"));
		mockMvc.perform(get(ME).cookie(missingMember)).andExpect(unauthorized());
	}

	@Test
	void 로그아웃하면_같은_이름과_Path로_Max_Age_0인_쿠키를_내려_지운다() throws Exception {
		signUp(customer("hong")).andExpect(status().isCreated());

		expectTokenCookieCleared(mockMvc.perform(post(LOG_OUT).cookie(logInCookie("hong"))));
		expectTokenCookieCleared(mockMvc.perform(post(LOG_OUT)));   // 토큰이 없거나 만료되어도 지운다
	}

	private void expectTokenCookieCleared(ResultActions result) throws Exception {
		result.andExpect(status().isNoContent())
			.andExpect(cookie().value(JwtTokenProvider.COOKIE_NAME, ""))
			.andExpect(cookie().maxAge(JwtTokenProvider.COOKIE_NAME, 0))
			.andExpect(cookie().path(JwtTokenProvider.COOKIE_NAME, "/"))
			.andExpect(cookie().httpOnly(JwtTokenProvider.COOKIE_NAME, true))
			.andExpect(cookie().secure(JwtTokenProvider.COOKIE_NAME, true))
			.andExpect(cookie().sameSite(JwtTokenProvider.COOKIE_NAME, "Lax"));
	}

	private Cookie logInCookie(String loginId) throws Exception {
		return logIn(loginId, PASSWORD)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(JwtTokenProvider.COOKIE_NAME);
	}

	private ResultActions signUp(Body body) throws Exception {
		return mockMvc.perform(post(SIGN_UP).contentType(MediaType.APPLICATION_JSON).content(body.json()));
	}

	private ResultActions logIn(String loginId, String password) throws Exception {
		return mockMvc.perform(post(LOG_IN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"loginId\": %s, \"password\": %s}".formatted(quoted(loginId), quoted(password))));
	}

	private static ResultMatcher badRequest(String message) {
		return result -> {
			status().isBadRequest().match(result);
			content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT).match(result);
		};
	}

	private static ResultMatcher unauthorized() {
		return result -> {
			status().isUnauthorized().match(result);
			content().json("{\"message\": \"로그인이 필요합니다.\"}", JsonCompareMode.STRICT).match(result);
		};
	}

	private static Body customer(String loginId) {
		return new Body(loginId, PASSWORD, "홍길동", "서울시 동대문구 서울시립대로 163", "010-1234-5678", "CUSTOMER", null);
	}

	private static Body staff(String loginId, String staffCode) {
		return new Body(loginId, PASSWORD, "김직원", "서울시 동대문구", "010-9876-5432", "STAFF", staffCode);
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	/** 회원가입 요청 본문. 빈 항목은 null로 준다. */
	record Body(String loginId, String password, String name, String address, String phone, String memberType,
			String staffCode) {

		Body withLoginId(String value) {
			return new Body(value, password, name, address, phone, memberType, staffCode);
		}

		Body withPassword(String value) {
			return new Body(loginId, value, name, address, phone, memberType, staffCode);
		}

		Body withName(String value) {
			return new Body(loginId, password, value, address, phone, memberType, staffCode);
		}

		Body withAddress(String value) {
			return new Body(loginId, password, name, value, phone, memberType, staffCode);
		}

		Body withPhone(String value) {
			return new Body(loginId, password, name, address, value, memberType, staffCode);
		}

		Body withMemberType(String value) {
			return new Body(loginId, password, name, address, phone, value, staffCode);
		}

		Body withStaffCode(String value) {
			return new Body(loginId, password, name, address, phone, memberType, value);
		}

		String json() {
			return """
					{"loginId": %s, "password": %s, "name": %s, "address": %s, "phone": %s, "memberType": %s, \
					"staffCode": %s}""".formatted(quoted(loginId), quoted(password), quoted(name), quoted(address),
					quoted(phone), quoted(memberType), quoted(staffCode));
		}

	}

}
