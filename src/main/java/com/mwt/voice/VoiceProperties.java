package com.mwt.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * voice 설정. application.yml이 환경변수 OPENAI_API_KEY를 openai-api-key에 넣는다.
 */
@ConfigurationProperties("voice")
record VoiceProperties(String openaiApiKey) {

	/** .env 값 끝에 붙은 공백이 키에 섞이면 호출마다 401이 나므로 앞뒤 공백을 뗀다. */
	VoiceProperties {
		openaiApiKey = openaiApiKey == null ? null : openaiApiKey.strip();
	}

	boolean hasOpenaiApiKey() {
		return openaiApiKey != null && !openaiApiKey.isEmpty();
	}

	@Override
	public String toString() {
		return "VoiceProperties[openaiApiKey=" + (hasOpenaiApiKey() ? "(있음)" : "(없음)") + "]";
	}

}
