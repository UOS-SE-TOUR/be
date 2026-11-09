package com.mwt.tour;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 매일 0시(서울)에 여행 상품의 상태를 바꾸는 예약 작업이다(설계산출물 4.4, 1.3 (9)). 오늘 날짜는 Clock에서 받는다.
 * 예약 작업은 SchedulingConfig(@EnableScheduling)가 켠다.
 */
@Component
@RequiredArgsConstructor
class TourScheduler {

	private final TourService tourService;
	private final Clock clock;

	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	void updateStatuses() {
		tourService.updateStatuses(LocalDate.now(clock));
	}

}
