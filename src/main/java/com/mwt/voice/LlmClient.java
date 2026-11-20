package com.mwt.voice;

import java.util.List;

/**
 * 음성 대화에서 시스템이 할 말과 고친 내용을 받는다(설계산출물 4.7). 정확도가 모자라면 더 큰 모델로 바꿀 수 있게 구현(OpenAiLlmClient)과 나눴다.
 */
interface LlmClient {

	/**
	 * 상품 목록과 지금 고른 내용(context), 지금까지의 대화(messages, 마지막 줄이 고객의 말)를 주고 답을 받는다.
	 * 답은 서버가 확인하기 전의 것이라 목록에 없는 상품·등급·옵션이 들어 있을 수 있다 — VoiceService가 거른다.
	 * 부르지 못했거나(키 없음, 실패, 시간 초과) 답을 해석하지 못하면 503 ResponseStatusException을 던진다 —
	 * 화면은 그때 손으로 고르라고 안내한다(설계산출물 5.5 부 흐름).
	 */
	VoiceChatResponse chat(VoiceContext context, List<VoiceMessage> messages);

}
