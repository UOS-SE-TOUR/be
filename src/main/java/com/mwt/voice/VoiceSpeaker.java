package com.mwt.voice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화의 한 줄을 말한 쪽이다.
 */
@Schema(enumAsRef = true, description = "말한 쪽. SYSTEM(시스템: 화면의 인사와 서버가 돌려준 말), CUSTOMER(고객)")
public enum VoiceSpeaker {

	SYSTEM,
	CUSTOMER

}
