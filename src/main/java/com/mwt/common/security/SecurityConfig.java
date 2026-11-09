package com.mwt.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.mwt.common.exception.ErrorResponse;
import com.mwt.common.exception.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 API 표(설계산출물 4.9)의 "쓸 수 있는 사람"대로 주소마다 막는다.
 * 표에 없는 주소는 모두 막으므로, 표의 주소 꼴 밖에 API를 새로 만들면 여기에 규칙을 더한다.
 * 로그인하지 않았거나 만료되었으면 401, 권한이 없으면 403을 {"message": "..."}로 돌려준다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

	/** 회원 구분 이름. member 모듈의 MemberType 이름과 같아야 한다(SecurityConfigTest가 확인한다). */
	static final String CUSTOMER = "CUSTOMER";
	static final String STAFF = "STAFF";

	/** hasRole(...)이 권한 이름 앞에 붙여 찾는 말. */
	static final String ROLE_PREFIX = "ROLE_";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider, JsonMapper jsonMapper,
			@Value("${spring.web.error.path:${error.path:/error}}") String errorPath) throws Exception {
		http
			// 4.10: 쿠키가 SameSite=Lax이고 상태를 바꾸는 GET이 없으므로 CSRF 토큰을 두지 않는다
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)   // 로그아웃은 member 모듈의 API가 쿠키를 지운다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/v1/members/signup", "/api/v1/members/login",
						"/api/v1/members/logout").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/members/me").authenticated()
				.requestMatchers("/api/v1/tours/**", "/api/v1/bookings/**", "/api/v1/voice/**").hasRole(CUSTOMER)
				.requestMatchers("/api/v1/staff/**").hasRole(STAFF)
				// API 문서(Swagger)와, 필터·서블릿 컨테이너 오류를 {"message"}로 바꾸는 주소(ApiErrorController)
				.requestMatchers("/api/swagger-ui.html", "/api/swagger-ui/**", "/api/docs", "/api/docs.yaml", "/api/docs/**",
						errorPath)
				.permitAll()
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, e) ->
						writeError(response, HttpStatus.UNAUTHORIZED, jsonMapper))
				.accessDeniedHandler((request, response, e) -> writeError(response, HttpStatus.FORBIDDEN, jsonMapper)))
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private static void writeError(HttpServletResponse response, HttpStatus status, JsonMapper jsonMapper)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8);
		jsonMapper.writeValue(response.getWriter(), new ErrorResponse(GlobalExceptionHandler.messageFor(status)));
	}

}
