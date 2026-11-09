package com.mwt.tour;

import lombok.Getter;

/**
 * 주제의 네 테마다. 테마마다 화면 이름과 등급에 상관없이 제공하는 테마 서비스·기념품·현지 차량을 담는다
 * (설계산출물 4.4, 분석산출물 v0.5 1.4 표 2).
 */
@Getter
public enum Theme {

	HONEYMOON("허니문 낭만 투어", "로맨틱 스페셜 룸 데코레이션(2인 전용)", "커플 기념티", "2인 전용 고급 차량"),
	PARENTS("효도 힐링 투어", "고품격 안마·지압 서비스", "건강 인삼 기념품", "10인 승합 고급 차량"),
	GOLF("골프 챌린지 투어", "유명 골프 리조트 테마 연출", "골프 액세서리(골프공)", "10인 승합 고급 차량"),
	TREKKING("아웃도어 트레킹 투어", "트레킹·산악 테마 어드벤처 연출", "아웃도어 기념품(스카프)", "10인 승합 고급 차량");

	private final String displayName;
	private final String service;
	private final String souvenir;
	private final String vehicle;

	Theme(String displayName, String service, String souvenir, String vehicle) {
		this.displayName = displayName;
		this.service = service;
		this.souvenir = souvenir;
		this.vehicle = vehicle;
	}

}
