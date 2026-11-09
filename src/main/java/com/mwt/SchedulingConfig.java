package com.mwt;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 예약 작업(@Scheduled)을 켠다. 지금은 매일 0시에 여행 상품 상태를 바꾸는 tour.TourScheduler 하나다.
 * 모듈 패키지의 슬라이스 테스트(@WebMvcTest 등)에는 들어가지 않아 그 테스트에서는 예약 작업이 돌지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
