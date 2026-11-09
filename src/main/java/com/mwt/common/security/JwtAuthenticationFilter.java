package com.mwt.common.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 요청마다 accessToken 쿠키에서 토큰을 꺼내 확인하고, 맞으면 회원 ID와 회원 구분을 Spring Security에 넣는다(설계산출물 4.3).
 * 토큰이 없거나 틀리거나 만료되었으면 아무것도 넣지 않고 넘긴다. 막을지는 {@link SecurityConfig}의 주소별 규칙이 정한다 —
 * "누구나" 주소는 토큰이 만료되었어도 통과하고, 나머지 주소는 401을 받는다.
 * 빈으로 등록하지 않는다. 빈이면 스프링 부트가 서블릿 필터로도 한 번 더 등록한다.
 */
@Slf4j
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		findClaims(request).ifPresent(claims -> {
			UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
					claims.memberId(), null, List.of(new SimpleGrantedAuthority(SecurityConfig.ROLE_PREFIX + claims.memberType())));
			SecurityContext context = contextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			contextHolder.setContext(context);
		});
		chain.doFilter(request, response);
	}

	/** 같은 이름의 쿠키가 여럿 오면(경로가 다른 옛 쿠키 등) 받아들일 수 있는 첫 토큰을 쓴다. */
	private Optional<TokenClaims> findClaims(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> JwtTokenProvider.COOKIE_NAME.equals(cookie.getName()))
			.map(cookie -> parse(cookie.getValue()))
			.filter(Objects::nonNull)
			.findFirst();
	}

	private TokenClaims parse(String token) {
		try {
			return jwtTokenProvider.parseToken(token);
		} catch (InvalidTokenException e) {
			log.debug("로그인 토큰을 받아들이지 않음: {}", e.getMessage());
			return null;
		}
	}

}
