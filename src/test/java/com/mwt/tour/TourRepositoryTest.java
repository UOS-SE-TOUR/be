package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;

import jakarta.persistence.EntityManager;

/**
 * 상품 찾기 쿼리(설계산출물 4.4 TourRepository)를 MySQL(Testcontainers)에서 본다. 테스트마다 되돌린다.
 * "오늘"은 2026-11-10이다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class TourRepositoryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	@Autowired
	TourRepository tourRepository;

	@Autowired
	EntityManager entityManager;

	@Test
	void 다가오는_상품은_오늘과_지난_상품을_빼고_마감_상품은_넣는다() {
		Tour later = save(Theme.GOLF, "2026-12-01", 3);
		Tour tomorrow = save(Theme.GOLF, "2026-11-11", 5);
		Tour full = save(Theme.GOLF, "2026-11-20", 4);
		save(Theme.GOLF, "2026-11-10", 5);
		save(Theme.GOLF, "2026-11-09", 5);
		save(Theme.TREKKING, "2026-11-20", 5);
		makeFull(full);

		assertThat(tourRepository.findUpcomingTours(Theme.GOLF, TODAY))
			.extracting(Tour::getId, Tour::getStatus)
			.containsExactly(
					tuple(tomorrow.getId(), TourStatus.RECRUITING),
					tuple(full.getId(), TourStatus.FULL),
					tuple(later.getId(), TourStatus.RECRUITING));
	}

	@Test
	void 출발일이_같은_상품은_기간_순이다() {
		Tour longer = save(Theme.GOLF, "2026-11-20", 7);
		Tour shorter = save(Theme.GOLF, "2026-11-20", 3);

		assertThat(tourRepository.findUpcomingTours(Theme.GOLF, TODAY))
			.extracting(Tour::getId)
			.containsExactly(shorter.getId(), longer.getId());
	}

	@Test
	void 테마와_출발일이_같은_상품만_기간_순으로_찾는다() {
		Tour longer = save(Theme.PARENTS, "2026-11-20", 7);
		Tour shorter = save(Theme.PARENTS, "2026-11-20", 3);
		Tour full = save(Theme.PARENTS, "2026-11-20", 5);
		save(Theme.PARENTS, "2026-11-21", 5);
		save(Theme.GOLF, "2026-11-20", 5);
		makeFull(full);

		assertThat(tourRepository.findByThemeAndDepartureDate(Theme.PARENTS, LocalDate.of(2026, 11, 20)))
			.extracting(Tour::getId)
			.containsExactly(shorter.getId(), full.getId(), longer.getId());
	}

	@Test
	void 잠그며_읽기는_ID로_상품을_찾는다() {
		Tour tour = save(Theme.GOLF, "2026-11-20", 3);
		entityManager.clear();

		assertThat(tourRepository.findByIdForUpdate(tour.getId())).get().extracting(Tour::getTheme).isEqualTo(Theme.GOLF);
		assertThat(tourRepository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
	}

	@Test
	void 출발할_상품은_출발일이_오늘이거나_지난_모집_중_확정_마감_상품이다() {
		Tour today = save(Theme.GOLF, "2026-11-10", 3);
		Tour confirmed = save(Theme.GOLF, "2026-11-09", 3);
		Tour full = save(Theme.TREKKING, "2026-11-01", 3);
		save(Theme.GOLF, "2026-11-11", 3);
		Tour inProgress = save(Theme.GOLF, "2026-11-08", 5);
		Tour cancelled = save(Theme.GOLF, "2026-11-05", 3);
		change(confirmed, tour -> tour.addApplicants(3));
		makeFull(full);
		change(inProgress, tour -> {
			tour.addApplicants(3);
			tour.depart();
		});
		change(cancelled, Tour::depart);

		assertThat(tourRepository.findDeparting(TODAY))
			.extracting(Tour::getId)
			.containsExactly(today.getId(), confirmed.getId(), full.getId());
	}

	@Test
	void 끝난_상품은_여행_종료일이_오늘보다_앞인_진행_중_상품이다() {
		Tour endedYesterday = inProgress("2026-11-06", 4);
		inProgress("2026-11-07", 4);
		Tour oneDayYesterday = inProgress("2026-11-09", 1);
		inProgress("2026-11-10", 1);
		Tour lastMonth = inProgress("2026-10-30", 3);
		Tour notDeparted = save(Theme.GOLF, "2026-11-01", 2);
		change(notDeparted, tour -> tour.addApplicants(3));
		Tour completed = inProgress("2026-11-01", 2);
		change(completed, Tour::complete);

		assertThat(tourRepository.findFinished(TODAY))
			.extracting(Tour::getId)
			.containsExactly(endedYesterday.getId(), oneDayYesterday.getId(), lastMonth.getId());
	}

	/** 출발일 하루 전을 "오늘"로 넣어 만든다. 그래서 오늘이거나 지난 출발일의 상품도 저장할 수 있다. */
	private Tour save(Theme theme, String departureDate, int duration) {
		LocalDate date = LocalDate.parse(departureDate);
		return tourRepository.saveAndFlush(new Tour(theme, date, duration, 10, date.minusDays(1)));
	}

	/** 신청 인원을 정원까지 채워 마감으로 만든다. */
	private void makeFull(Tour tour) {
		change(tour, saved -> saved.addApplicants(saved.remainingSeats()));
	}

	/** 3명이 신청해 확정된 뒤 출발한 상품이다. */
	private Tour inProgress(String departureDate, int duration) {
		Tour tour = save(Theme.GOLF, departureDate, duration);
		change(tour, saved -> {
			saved.addApplicants(3);
			saved.depart();
		});
		return tour;
	}

	/** 전이 메소드로 상태를 바꿔 DB에 쓰고, 쿼리가 DB에서 다시 읽도록 영속성 컨텍스트를 비운다. */
	private void change(Tour tour, Consumer<Tour> transition) {
		Tour saved = tourRepository.findById(tour.getId()).orElseThrow();
		transition.accept(saved);
		tourRepository.flush();
		entityManager.clear();
	}

}
