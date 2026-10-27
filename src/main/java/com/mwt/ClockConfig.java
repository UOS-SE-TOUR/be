package com.mwt;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘"과 현재 시각은 이 시계에서만 얻는다. 서버 시간대(UTC)와 상관없이 한국 날짜로 판단하고,
 * 테스트에서는 Clock.fixed(...)로 바꿔 끼워 날짜를 고정한다.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
