package com.mwt.voice;

/**
 * 음성에서 나온 글자에서 선택 항목을 뽑는다(설계산출물 v0.5 4.7). 정확도가 모자라면 더 큰 모델로 바꿀 수 있게 구현(OpenAiLlmClient)과 나눴다.
 */
interface LlmClient {

	/**
	 * 테마, 출발일, 투어 등급, 인원만 채운 TourSelection을 돌려준다(후보 상품은 빈 목록). 뽑지 못한 항목은 null이다.
	 * 부르지 못했거나(키 없음, 실패, 시간 초과) 답을 해석하지 못하면 503 ResponseStatusException을 던진다 —
	 * 화면은 그때 손으로 고르라고 안내한다(설계산출물 v0.5 5.5 부 흐름).
	 */
	TourSelection extractSelection(String text);

}
