package com.mwt.notification;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * SENS 설정 네 개가 모두 있으면 SensSmsSender를, 모두 비어 있으면 FakeSmsSender를 SmsSender 빈으로 둔다.
 * 일부만 있으면 시연 때 문자가 안 나가는 걸 늦게 알게 되므로 서버를 띄우지 않는다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SensProperties.class)
class SmsSenderConfig {

	// 문자는 신청 응답 전에 보내므로 SENS가 응답하지 않을 때 신청 응답이 오래 묶이지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	SmsSender smsSender(SensProperties properties, Clock clock) {
		if (properties.isEmpty()) {
			log.warn("NCP SENS 설정이 없어 문자를 보내지 않고 로그로만 남깁니다(가짜 발송기)");
			return new FakeSmsSender();
		}
		List<String> missing = properties.missingNames();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("NCP SENS 설정이 일부만 있습니다. 비어 있는 환경변수: " + String.join(", ", missing)
					+ " (네 개를 모두 넣거나 모두 비우세요)");
		}
		return new SensSmsSender(sensRestClient(), properties, clock);
	}

	private static RestClient sensRestClient() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(requestFactory).build();
	}

}
