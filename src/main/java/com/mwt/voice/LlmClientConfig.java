package com.mwt.voice;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAiLlmClient를 LlmClient 빈으로 둔다. OPENAI_API_KEY가 없어도 서버는 띄운다 — 그때 음성으로 고르기만 503이고,
 * 화면에서 직접 고르는 흐름(MWT-UC4)은 그대로 쓸 수 있다(설계산출물 v0.5 5.5 부 흐름).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VoiceProperties.class)
class LlmClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	// 고객이 말한 뒤 기다리는 시간이다. 넘으면 503으로 돌려주고 화면이 손으로 고르라고 안내한다.
	// 스키마를 처음 쓰는 요청은 OpenAI가 스키마를 처리하느라 더 걸려서 넉넉히 둔다
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

	@Bean
	LlmClient llmClient(VoiceProperties properties, JsonMapper jsonMapper, Clock clock) {
		if (!properties.hasOpenaiApiKey()) {
			log.warn("OPENAI_API_KEY가 없어 음성으로 고르기(POST /api/v1/voice/parse)는 503을 돌려줍니다");
		}
		return new OpenAiLlmClient(openAiRestClient(), properties.openaiApiKey(), jsonMapper, clock);
	}

	private static RestClient openAiRestClient() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(requestFactory).build();
	}

}
