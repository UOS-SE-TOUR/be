package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import jakarta.persistence.EntityManager;

/**
 * 다른 모듈이 쓰는 상품 읽기(getTour)·신청 인원 더하기(addApplicants, booking)와
 * 예약 작업의 상태 바꾸기(updateStatuses), 직원의 출발·완료 처리(departTour·completeTour)를 MySQL(Testcontainers)에서 본다. 동시 신청은 TourConcurrencyTest가 본다.
 * "오늘"은 2026-11-10(서울)로 고정한다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, TourService.class, TourScheduler.class, TourServiceTest.FixedClock.class})
@DataJpaTest
class TourServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	@Autowired
	TourService tourService;

	@Autowired
	TourScheduler tourScheduler;

	@Autowired
	TourRepository tourRepository;

	@Autowired
	EntityManager entityManager;

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

	@Test
	void 신청_인원을_더해_저장하고_확정_변화를_돌려준다() {
		Tour tour = save(Theme.GOLF, "2026-11-20", 5, 2);

		assertThat(tourService.addApplicants(tour.getId(), 1)).isEqualTo(ConfirmChange.NEWLY_CONFIRMED);
		assertThat(reload(tour)).extracting(Tour::getAppliedCount, Tour::getStatus).containsExactly(3, TourStatus.CONFIRMED);
	}

	@Test
	void 없는_상품에_신청_인원을_더하면_404() {
		assertThatThrownBy(() -> tourService.addApplicants(Long.MAX_VALUE, 1))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> tourService.addApplicants(null, 1))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void 출발일이_되면_모집_중은_취소하고_확정_마감은_진행_중으로_바꾼다() {
		Tour recruiting = save(Theme.GOLF, "2026-11-10", 5, 2);
		Tour confirmed = save(Theme.GOLF, "2026-11-10", 5, 3);
		Tour full = save(Theme.TREKKING, "2026-11-10", 5, 10);
		Tour tomorrow = save(Theme.GOLF, "2026-11-11", 5, 3);

		tourService.updateStatuses(TODAY);

		assertThat(statusOf(recruiting)).isEqualTo(TourStatus.CANCELLED);
		assertThat(statusOf(confirmed)).isEqualTo(TourStatus.IN_PROGRESS);
		assertThat(statusOf(full)).isEqualTo(TourStatus.IN_PROGRESS);
		assertThat(statusOf(tomorrow)).isEqualTo(TourStatus.CONFIRMED);
	}

	@Test
	void 여행_종료일_다음_날에_완료한다() {
		Tour tour = save(Theme.GOLF, "2026-11-06", 4, 3);   // 종료일 11/9
		tourService.updateStatuses(LocalDate.of(2026, 11, 6));
		assertThat(statusOf(tour)).isEqualTo(TourStatus.IN_PROGRESS);

		tourService.updateStatuses(LocalDate.of(2026, 11, 9));
		assertThat(statusOf(tour)).isEqualTo(TourStatus.IN_PROGRESS);

		tourService.updateStatuses(LocalDate.of(2026, 11, 10));
		assertThat(statusOf(tour)).isEqualTo(TourStatus.COMPLETED);
	}

	@Test
	void 예약_작업은_서버가_꺼져_건너뛴_날의_상품도_오늘_날짜로_바꾼다() {
		Tour endedWhileOff = save(Theme.GOLF, "2026-11-05", 3, 3);   // 11/5 출발, 11/7 종료
		Tour notFilledWhileOff = save(Theme.GOLF, "2026-11-06", 5, 2);
		Tour departedWhileOff = save(Theme.TREKKING, "2026-11-08", 5, 10);   // 11/12 종료
		Tour upcoming = save(Theme.GOLF, "2026-11-11", 5, 3);
		Tour cancelledBefore = save(Theme.GOLF, "2026-11-01", 5, 1);
		Tour completedBefore = save(Theme.GOLF, "2026-11-01", 2, 3);
		tourService.updateStatuses(LocalDate.of(2026, 11, 4));

		tourScheduler.updateStatuses();   // 오늘은 FixedClock의 2026-11-10

		assertThat(statusOf(endedWhileOff)).isEqualTo(TourStatus.COMPLETED);
		assertThat(statusOf(notFilledWhileOff)).isEqualTo(TourStatus.CANCELLED);
		assertThat(statusOf(departedWhileOff)).isEqualTo(TourStatus.IN_PROGRESS);
		assertThat(statusOf(upcoming)).isEqualTo(TourStatus.CONFIRMED);
		assertThat(statusOf(cancelledBefore)).isEqualTo(TourStatus.CANCELLED);
		assertThat(statusOf(completedBefore)).isEqualTo(TourStatus.COMPLETED);
	}

	@Test
	void 직원이_출발_처리하면_출발일_전이어도_모집_중은_취소하고_확정_마감은_진행_중으로_바꾼다() {
		Tour recruiting = save(Theme.GOLF, "2026-12-01", 5, 2);
		Tour confirmed = save(Theme.GOLF, "2026-12-01", 5, 3);
		Tour full = save(Theme.TREKKING, "2026-12-01", 5, 10);

		assertThat(tourService.departTour(recruiting.getId()).status()).isEqualTo(TourStatus.CANCELLED);
		assertThat(tourService.departTour(confirmed.getId()).status()).isEqualTo(TourStatus.IN_PROGRESS);
		assertThat(tourService.departTour(full.getId()).status()).isEqualTo(TourStatus.IN_PROGRESS);

		assertThat(statusOf(recruiting)).isEqualTo(TourStatus.CANCELLED);
		assertThat(statusOf(confirmed)).isEqualTo(TourStatus.IN_PROGRESS);
		assertThat(statusOf(full)).isEqualTo(TourStatus.IN_PROGRESS);
	}

	@Test
	void 직원이_완료_처리하면_여행_종료일_전이어도_진행_중을_완료로_바꾼다() {
		Tour tour = save(Theme.GOLF, "2026-12-01", 5, 3);
		tourService.departTour(tour.getId());
		reload(tour);   // 출발과 완료는 요청이 달라 트랜잭션도 따로다

		StaffTourSummary completed = tourService.completeTour(tour.getId());

		assertThat(completed).extracting(StaffTourSummary::tourId, StaffTourSummary::appliedCount, StaffTourSummary::status)
			.containsExactly(tour.getId(), 3, TourStatus.COMPLETED);
		assertThat(statusOf(tour)).isEqualTo(TourStatus.COMPLETED);
	}

	@Test
	void 출발_처리는_모집_중_확정_마감만_완료_처리는_진행_중만_하고_아니면_409() {
		Tour cancelled = save(Theme.GOLF, "2026-12-01", 5, 2);
		tourService.departTour(cancelled.getId());
		reload(cancelled);
		Tour recruiting = save(Theme.GOLF, "2026-12-02", 5, 0);

		assertThatThrownBy(() -> tourService.departTour(cancelled.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(e.getMessage()).isEqualTo("모집 중·확정·마감인 상품만 출발 처리할 수 있습니다.");
			});
		assertThatThrownBy(() -> tourService.completeTour(recruiting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(e.getMessage()).isEqualTo("진행 중인 상품만 완료 처리할 수 있습니다.");
			});
		assertThat(statusOf(cancelled)).isEqualTo(TourStatus.CANCELLED);
		assertThat(statusOf(recruiting)).isEqualTo(TourStatus.RECRUITING);
	}

	@Test
	void 없는_상품을_출발_완료_처리하면_404() {
		assertThatThrownBy(() -> tourService.departTour(Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> tourService.completeTour(Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	/** 출발일 하루 전을 "오늘"로 넣어 만든다. 그래서 오늘이거나 지난 출발일의 상품도 저장할 수 있다. */
	private Tour save(Theme theme, String departureDate) {
		return save(theme, departureDate, 5, 0);
	}

	/** 정원 10명인 상품을 만들고 신청 인원을 더해 저장한다(3명 이상이면 확정, 10명이면 마감). */
	private Tour save(Theme theme, String departureDate, int duration, int applicants) {
		LocalDate date = LocalDate.parse(departureDate);
		Tour tour = new Tour(theme, date, duration, 10, date.minusDays(1));
		if (applicants > 0) {
			tour.addApplicants(applicants);
		}
		return tourRepository.saveAndFlush(tour);
	}

	private TourStatus statusOf(Tour tour) {
		return reload(tour).getStatus();
	}

	/** 바뀐 것을 DB에 쓰고 영속성 컨텍스트를 비운 뒤 DB에서 다시 읽는다. */
	private Tour reload(Tour tour) {
		tourRepository.flush();
		entityManager.clear();
		return tourRepository.findById(tour.getId()).orElseThrow();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		}

	}

}
