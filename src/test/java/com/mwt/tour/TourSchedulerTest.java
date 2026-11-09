package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매일 0시 예약 작업(설계산출물 4.4 TourScheduler, 1.3 (9)). 상태를 바꾸는 일 자체는 TourServiceTest가 본다.
 */
class TourSchedulerTest {

	@Test
	void 서버_시간대와_상관없이_서울_날짜를_오늘로_넘긴다() {
		TourService tourService = mock(TourService.class);
		Clock seoulMidnight = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("Asia/Seoul"));   // UTC로는 아직 11/9

		new TourScheduler(tourService, seoulMidnight).updateStatuses();

		verify(tourService).updateStatuses(LocalDate.of(2026, 11, 10));
	}

	@Test
	void 매일_0시_서울_시간에_돈다() throws NoSuchMethodException {
		Scheduled scheduled = TourScheduler.class.getDeclaredMethod("updateStatuses").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

}
