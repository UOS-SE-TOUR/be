package com.mwt;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘"과 현재 시각은 이 시계에서만 얻는다. 서버 시간대(UTC)와 상관없이 한국 날짜로 판단한다.
 * 테스트에서 날짜를 고정할 때는 이 빈을 @MockitoBean·@TestBean으로 바꾸거나 @Primary인 Clock.fixed(...) 빈을 더한다.
 * 같은 이름(clock)으로 빈을 하나 더 정의하면 스프링이 빈 덮어쓰기를 막아 컨텍스트가 뜨지 않는다.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
