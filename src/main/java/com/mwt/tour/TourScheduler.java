package com.mwt.tour;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 0시(서울)와 서버가 뜰 때 여행 상품의 상태를 바꾸는 예약 작업이다(설계산출물 4.4, 1.3 (9)). 오늘 날짜는 Clock에서 받는다.
 * 예약 작업은 SchedulingConfig(@EnableScheduling)가 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TourScheduler {

	private final TourService tourService;
	private final Clock clock;

	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	void updateStatuses() {
		tourService.updateStatuses(LocalDate.now(clock));
	}

	/**
	 * 0시에 서버가 꺼져 있었던 날의 상품을 다음 0시까지 기다리지 않고 바꾼다. 날짜로 찾으므로 여러 번 돌아도 결과가 같다.
	 * 여기서 실패해도 서버는 뜨고, 다음 0시에 다시 돈다.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void updateStatusesOnStartup() {
		try {
			updateStatuses();
		} catch (RuntimeException e) {
			log.error("서버 시작 때 상품 상태를 바꾸지 못했다. 다음 0시에 다시 돈다.", e);
		}
	}

}
