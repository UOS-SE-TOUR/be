package com.mwt.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 토큰 설정. application.yml이 환경변수 JWT_SECRET을 넣는다.
 */
@ConfigurationProperties("jwt")
record JwtProperties(String secret) {

	/** .env 값 끝에 공백이 붙으면 같은 값을 넣었다고 생각해도 다른 키가 되므로 앞뒤 공백을 뗀다. */
	JwtProperties {
		secret = secret == null ? null : secret.strip();
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=(숨김)]";
	}

}
