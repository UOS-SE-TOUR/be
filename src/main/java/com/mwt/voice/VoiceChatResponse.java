package com.mwt.voice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화의 답이다. LlmClient가 돌려준 답을 VoiceService가 상품 목록으로 확인한 뒤 화면에 돌려준다.
 */
@Schema(description = "음성 대화의 답")
public record VoiceChatResponse(
		@Schema(description = "시스템이 할 말. 화면이 글로 보이고 소리로 읽는다. 1~1000자") String reply,
		@Schema(description = "이번 말까지 반영한 고른 내용. 서버가 상품 목록으로 확인한 값만 들어 있다") VoiceDraft draft,
		VoiceNext next) {
}
