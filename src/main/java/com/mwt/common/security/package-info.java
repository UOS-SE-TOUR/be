/**
 * common.security 모듈: 로그인 토큰(JWT) 만들기와 확인, API별 권한 검사, 비밀번호 암호화. 업무 모듈이 아닌 공통 패키지다.
 *
 * <p>유즈케이스: MWT-UC2. 클래스 구성은 설계산출물 4.3절 "common.security 패키지 클래스도"를 따른다.
 * 주소별 권한 규칙은 4.9 서버 API 표, 쿠키와 CSRF는 4.10을 따른다.
 *
 * <p>Controller는 로그인한 회원의 ID를 {@code @AuthenticationPrincipal Long memberId}로 받는다.
 */
package com.mwt.common.security;
