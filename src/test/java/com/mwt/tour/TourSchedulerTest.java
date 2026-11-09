package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매일 0시와 서버가 뜰 때 도는 예약 작업(설계산출물 4.4 TourScheduler, 1.3 (9)). 상태를 바꾸는 일 자체는 TourServiceTest가 본다.
 */
class TourSchedulerTest {

	// UTC로는 아직 11/9지만 서울은 11/10 0시다
	private static final Clock SEOUL_MIDNIGHT = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("Asia/Seoul"));

	private final TourService tourService = mock(TourService.class);
	private final TourScheduler tourScheduler = new TourScheduler(tourService, SEOUL_MIDNIGHT);

	@Test
	void 서버_시간대와_상관없이_서울_날짜를_오늘로_넘긴다() {
		tourScheduler.updateStatuses();

		verify(tourService).updateStatuses(LocalDate.of(2026, 11, 10));
	}

	@Test
	void 매일_0시_서울_시간에_돈다() throws NoSuchMethodException {
		Scheduled scheduled = TourScheduler.class.getDeclaredMethod("updateStatuses").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void 서버가_뜰_때도_오늘_날짜로_한_번_돈다() throws NoSuchMethodException {
		EventListener listener = TourScheduler.class.getDeclaredMethod("updateStatusesOnStartup").getAnnotation(EventListener.class);

		tourScheduler.updateStatusesOnStartup();

		assertThat(listener.value()).containsExactly(ApplicationReadyEvent.class);
		verify(tourService).updateStatuses(LocalDate.of(2026, 11, 10));
	}

	@Test
	void 서버가_뜰_때_실패해도_서버를_멈추지_않는다() {
		doThrow(new IllegalStateException("잠금 대기 시간 초과")).when(tourService).updateStatuses(any());

		assertThatCode(tourScheduler::updateStatusesOnStartup).doesNotThrowAnyException();
	}

}
