package com.mwt.tour;

import com.mwt.common.exception.BusinessException;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 주제의 네 테마다. 테마마다 화면 이름과 등급에 상관없이 제공하는 테마 서비스·기념품·현지 차량을 담는다
 * (설계산출물 4.4, 분석산출물 v0.5 1.4 표 2).
 * 테마 규칙 셋도 여기 있다: 허니문과 효도 투어는 그랜드와 프리미엄만, 허니문은 한 번에 2명, 허니문과 효도 투어는 호텔 4등급 이상.
 * API 문서의 설명은 화면 이름을 손으로 옮긴 것이니 화면 이름을 바꾸면 함께 고친다.
 */
@Schema(enumAsRef = true, description = "테마. HONEYMOON(허니문 낭만 투어), PARENTS(효도 힐링 투어), "
		+ "GOLF(골프 챌린지 투어), TREKKING(아웃도어 트레킹 투어)")
@Getter
public enum Theme {

	HONEYMOON("허니문 낭만 투어", "로맨틱 스페셜 룸 데코레이션(2인 전용)", "커플 기념티", "2인 전용 고급 차량"),
	PARENTS("효도 힐링 투어", "고품격 안마·지압 서비스", "건강 인삼 기념품", "10인 승합 고급 차량"),
	GOLF("골프 챌린지 투어", "유명 골프 리조트 테마 연출", "골프 액세서리(골프공)", "10인 승합 고급 차량"),
	TREKKING("아웃도어 트레킹 투어", "트레킹·산악 테마 어드벤처 연출", "아웃도어 기념품(스카프)", "10인 승합 고급 차량");

	static final int HONEYMOON_HEADCOUNT = 2;

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

	public boolean allows(TourLevel level) {
		return level != null && (level != TourLevel.CLASSIC || !grandAndPremiumOnly());
	}

	public void checkHeadcount(int headcount) {
		if (this == HONEYMOON && headcount != HONEYMOON_HEADCOUNT) {
			throw new BusinessException(displayName + "는 " + HONEYMOON_HEADCOUNT + "명만 신청할 수 있습니다.");
		}
	}

	public int minHotelGrade() {
		return grandAndPremiumOnly() ? TourLevel.GRAND.getHotelGrade() : TourLevel.CLASSIC.getHotelGrade();
	}

	private boolean grandAndPremiumOnly() {
		return this == HONEYMOON || this == PARENTS;
	}

}
