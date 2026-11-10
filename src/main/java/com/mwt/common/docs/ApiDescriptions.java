package com.mwt.common.docs;

/**
 * API 문서에 여러 번 쓰는 설명 문구. 실패 응답 설명은 Controller의 @ApiResponse가, 칸 설명은 DTO의 @Schema가 쓴다.
 */
public final class ApiDescriptions {

	public static final String UNAUTHORIZED = "로그인하지 않았거나 로그인이 만료됨";
	public static final String NOT_CUSTOMER = "고객 계정이 아님";
	public static final String NOT_STAFF = "직원 계정이 아님";
	public static final String TOUR_NOT_FOUND = "여행 상품이 없음";

	/** 상품 이름. 이름을 만드는 TourSummary.title()의 형식을 바꾸면 함께 고친다. */
	public static final String TOUR_TITLE = "상품 이름(테마 이름·출발일·기간, 예: 허니문 낭만 투어 10/20 출발 5일)";
	public static final String DURATION_DAYS = "여행 기간(일)";
	public static final String LEVEL_NAME = "투어 등급의 화면 이름(예: 그랜드)";
	public static final String PRICE_PER_PERSON = "1인 가격(원)";

	private ApiDescriptions() {
	}

}
