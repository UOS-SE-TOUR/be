package com.mwt.voice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화의 한 줄이다. 글자 수 상한과 빈 줄은 VoiceChatRequest가 확인한다.
 */
@Schema(description = "음성 대화의 한 줄")
public record VoiceMessage(VoiceSpeaker speaker,
		@Schema(description = "말. 고객 줄은 300자, 시스템 줄은 1000자까지") String text) {
}
