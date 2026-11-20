package com.mwt.voice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화에서 화면이 다음에 할 일이다.
 */
@Schema(enumAsRef = true, description = "다음에 할 일. TALK(고객의 말을 더 듣는다), "
		+ "APPLY(고객이 요약을 확인했고 더 필요한 것이 없다고 했다 — 화면이 고른 내용으로 신청한다), "
		+ "STOP(고객이 그만두겠다고 했다)")
public enum VoiceNext {

	TALK,
	APPLY,
	STOP

}
