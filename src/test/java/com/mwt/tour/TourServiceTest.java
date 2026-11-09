package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.common.exception.BusinessException;

/**
 * 다른 모듈이 쓰는 상품 읽기(getTour, booking)와 음성용 찾기(findTours, voice)를 MySQL(Testcontainers)에서 본다.
 * "오늘"은 2026-11-10(서울)로 고정한다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, TourService.class, TourServiceTest.FixedClock.class})
@DataJpaTest
class TourServiceTest {

	@Autowired
	TourService tourService;

	@Autowired
	TourRepository tourRepository;

	@Test
	void 음성용_찾기는_테마와_출발일이_같은_상품을_돌려준다() {
		Tour tour = save(Theme.TREKKING, "2026-11-11");
		save(Theme.TREKKING, "2026-11-12");
		save(Theme.GOLF, "2026-11-11");

		assertThat(tourService.findTours(Theme.TREKKING, LocalDate.of(2026, 11, 11)))
			.extracting(TourSummary::tourId, TourSummary::title)
			.containsExactly(tuple(tour.getId(), "아웃도어 트레킹 투어 11/11 출발 5일"));
	}

	@Test
	void 음성용_찾기는_출발일이_오늘이거나_지났으면_빈_목록이다() {
		save(Theme.TREKKING, "2026-11-10");
		save(Theme.TREKKING, "2026-11-09");

		assertThat(tourService.findTours(Theme.TREKKING, LocalDate.of(2026, 11, 10))).isEmpty();
		assertThat(tourService.findTours(Theme.TREKKING, LocalDate.of(2026, 11, 9))).isEmpty();
	}

	@Test
	void 음성용_찾기는_테마나_출발일을_못_뽑았으면_빈_목록이다() {
		save(Theme.TREKKING, "2026-11-11");

		assertThat(tourService.findTours(null, LocalDate.of(2026, 11, 11))).isEmpty();
		assertThat(tourService.findTours(Theme.TREKKING, null)).isEmpty();
	}

	@Test
	void 상품_읽기는_저장된_상품을_돌려준다() {
		Tour tour = save(Theme.GOLF, "2026-11-11");

		assertThat(tourService.getTour(tour.getId()).getTheme()).isEqualTo(Theme.GOLF);
	}

	@Test
	void 없는_상품을_읽으면_404() {
		assertThatThrownBy(() -> tourService.getTour(Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(e.getMessage()).isEqualTo("여행 상품을 찾을 수 없습니다.");
			});
	}

	@Test
	void 상품_ID가_비었으면_400() {
		assertThatThrownBy(() -> tourService.getTour(null))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void 옵션_목록은_옵션이_없는_상품이어도_투어_등급부터_확인한다() {
		Tour tour = save(Theme.HONEYMOON, "2026-11-11");

		assertThatThrownBy(() -> tourService.getOptions(tour.getId(), TourLevel.CLASSIC))
			.isInstanceOf(BusinessException.class)
			.hasMessage("허니문 낭만 투어는 그랜드와 프리미엄만 고를 수 있습니다.");
		assertThatThrownBy(() -> tourService.getOptions(tour.getId(), null))
			.isInstanceOf(BusinessException.class)
			.hasMessage("투어 등급을 골라 주세요.");
	}

	/** 출발일 하루 전을 "오늘"로 넣어 만든다. 그래서 오늘이거나 지난 출발일의 상품도 저장할 수 있다. */
	private Tour save(Theme theme, String departureDate) {
		LocalDate date = LocalDate.parse(departureDate);
		return tourRepository.saveAndFlush(new Tour(theme, date, 5, 10, date.minusDays(1)));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		}

	}

}
