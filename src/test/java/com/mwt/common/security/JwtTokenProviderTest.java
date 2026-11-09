package com.mwt.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenProviderTest {

	private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
	private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

	private final JwtTokenProvider provider = providerAt(NOW);

	@Test
	void 만든_토큰을_읽으면_회원_ID와_회원_구분() {
		String token = provider.createToken(42, "CUSTOMER");

		assertThat(provider.parseToken(token)).isEqualTo(new TokenClaims(42, "CUSTOMER"));
	}

	@Test
	void 만든_지_24시간까지는_읽히고_지나면_만료() {
		String token = provider.createToken(42, "STAFF");

		assertThat(providerAt(NOW.plus(JwtTokenProvider.VALIDITY).minusSeconds(1)).parseToken(token))
			.isEqualTo(new TokenClaims(42, "STAFF"));
		assertThatThrownBy(() -> providerAt(NOW.plus(JwtTokenProvider.VALIDITY).plusSeconds(1)).parseToken(token))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 다른_키로_서명한_토큰은_받지_않는다() {
		JwtTokenProvider other = new JwtTokenProvider(new JwtProperties("another-secret-0123456789abcdefghijkl"),
				Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
		String token = other.createToken(42, "STAFF");

		assertThatThrownBy(() -> provider.parseToken(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 내용을_바꾼_토큰은_받지_않는다() {
		String[] customer = provider.createToken(42, "CUSTOMER").split("\\.");
		String[] staff = provider.createToken(42, "STAFF").split("\\.");
		String forged = customer[0] + "." + staff[1] + "." + customer[2];   // 고객 서명에 직원 내용

		assertThatThrownBy(() -> provider.parseToken(forged)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 서명하지_않은_토큰은_받지_않는다() {
		String header = base64Url("{\"alg\":\"none\"}");
		String payload = base64Url("{\"sub\":\"42\",\"memberType\":\"STAFF\",\"exp\":" + NOW.plusSeconds(60).getEpochSecond()
				+ "}");

		assertThatThrownBy(() -> provider.parseToken(header + "." + payload + "."))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 형식이_틀리거나_빈_토큰은_받지_않는다() {
		assertThatThrownBy(() -> provider.parseToken("not-a-token")).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parseToken("")).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parseToken(null)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 같은_키로_서명했어도_만료_시각이나_회원_구분이나_숫자_회원_ID가_없으면_받지_않는다() {
		JwtClaimsSet noExpiry = JwtClaimsSet.builder().subject("42").claim("memberType", "STAFF").build();
		JwtClaimsSet noMemberType = JwtClaimsSet.builder().subject("42").expiresAt(NOW.plusSeconds(60)).build();
		JwtClaimsSet wrongSubject = JwtClaimsSet.builder()
			.subject("admin").claim("memberType", "STAFF").expiresAt(NOW.plusSeconds(60)).build();

		assertThatThrownBy(() -> provider.parseToken(signWithSameKey(noExpiry))).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parseToken(signWithSameKey(noMemberType)))
			.isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parseToken(signWithSameKey(wrongSubject)))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 회원_구분이_비면_토큰을_만들지_않는다() {
		assertThatThrownBy(() -> provider.createToken(42, " ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> provider.createToken(42, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 비밀_키가_비었거나_32바이트보다_짧으면_서버가_뜨지_않는다() {
		Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(null), clock))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("   "), clock))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("a".repeat(31)), clock))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("31바이트");
		new JwtTokenProvider(new JwtProperties("a".repeat(32)), clock);
	}

	@Test
	void 비밀_키_앞뒤_공백은_떼고_쓴다() {
		JwtTokenProvider padded = new JwtTokenProvider(new JwtProperties(SECRET + " \n"),
				Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

		assertThat(provider.parseToken(padded.createToken(42, "CUSTOMER"))).isEqualTo(new TokenClaims(42, "CUSTOMER"));
	}

	private static JwtTokenProvider providerAt(Instant instant) {
		return new JwtTokenProvider(new JwtProperties(SECRET), Clock.fixed(instant, ZoneId.of("Asia/Seoul")));
	}

	private static String signWithSameKey(JwtClaimsSet claims) {
		NimbusJwtEncoder encoder = NimbusJwtEncoder
			.withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
			.algorithm(MacAlgorithm.HS256)
			.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
