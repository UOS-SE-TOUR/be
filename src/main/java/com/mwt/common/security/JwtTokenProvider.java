package com.mwt.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 로그인 토큰(JWT)을 만들고 읽는다(설계산출물 4.3). 토큰에는 회원 ID와 회원 구분을 담고 24시간 뒤 만료된다.
 * 회원 구분을 문자열로 받아 common.security가 member 모듈을 알지 않게 한다.
 */
@Component
public class JwtTokenProvider {

	/** 토큰을 담는 쿠키 이름(설계산출물 4.10). 로그인·로그아웃 응답과 {@link JwtAuthenticationFilter}가 같이 쓴다. */
	public static final String COOKIE_NAME = "accessToken";

	/** 토큰이 유효한 시간. 로그인 쿠키의 Max-Age도 이 값과 같게 둔다(설계산출물 4.10). */
	public static final Duration VALIDITY = Duration.ofHours(24);

	static final String MEMBER_TYPE_CLAIM = "memberType";

	private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
	private static final int MIN_SECRET_BYTES = 32;   // HS256 키는 256비트 이상이어야 한다

	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Clock clock;

	JwtTokenProvider(JwtProperties properties, Clock clock) {
		SecretKey secretKey = secretKey(properties.secretKey());
		this.encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(ALGORITHM).build();
		this.decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(ALGORITHM).build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);   // 서버 한 대라 시계 차이를 봐주지 않는다
		timestampValidator.setClock(clock);
		timestampValidator.setAllowEmptyExpiryClaim(false);
		this.decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(timestampValidator));   // 기본 검증(typ 등)은 그대로 두고 시각 검증만 바꾼다
		this.clock = clock;
	}

	/** memberType은 MemberType의 이름(CUSTOMER, STAFF)이다. 다른 값이면 권한 규칙에 맞지 않으므로 만들지 않는다. */
	public String createToken(long memberId, String memberType) {
		if (!MemberTypes.contains(memberType)) {
			throw new IllegalArgumentException("모르는 회원 구분: " + memberType);
		}
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(Long.toString(memberId))
			.claim(MEMBER_TYPE_CLAIM, memberType)
			.issuedAt(now)
			.expiresAt(now.plus(VALIDITY))
			.build();
		JwsHeader header = JwsHeader.with(ALGORITHM).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** 서명과 만료를 확인하고 담긴 값을 돌려준다. 받아들일 수 없으면 {@link InvalidTokenException}을 던진다. */
	public TokenClaims parseToken(String token) {
		if (!StringUtils.hasText(token)) {
			throw new InvalidTokenException("토큰이 비어 있다");
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		} catch (JwtException e) {
			throw new InvalidTokenException(e.getMessage(), e);
		}
		String memberType = jwt.getClaimAsString(MEMBER_TYPE_CLAIM);
		if (!MemberTypes.contains(memberType)) {
			throw new InvalidTokenException("토큰의 회원 구분을 모른다: " + memberType);
		}
		try {
			return new TokenClaims(Long.parseLong(jwt.getSubject()), memberType);
		} catch (NumberFormatException e) {
			throw new InvalidTokenException("토큰의 회원 ID가 숫자가 아니다", e);
		}
	}

	private static SecretKey secretKey(String secret) {
		if (!StringUtils.hasText(secret)) {
			throw new IllegalStateException("환경변수 JWT_SECRET이 비어 있습니다. README 환경변수 절을 보고 넣어 주세요.");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET이 너무 짧습니다(" + bytes.length + "바이트). " + MIN_SECRET_BYTES
					+ "바이트 이상으로 넣어 주세요(예: openssl rand -hex 32).");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
