package com.mwt.member;

/**
 * 로그인과 내 정보의 응답(설계산출물 4.10). 화면은 토큰을 읽을 수 없어 이 값을 AuthStore에 둔다.
 */
public record MemberResponse(MemberType memberType, String name) {

	static MemberResponse from(Member member) {
		return new MemberResponse(member.getMemberType(), member.getName());
	}

}
