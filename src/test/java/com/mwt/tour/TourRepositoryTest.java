package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;

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

	/** 출발일 하루 전을 "오늘"로 넣어 만든다. 그래서 오늘이거나 지난 출발일의 상품도 저장할 수 있다. */
	private Tour save(Theme theme, String departureDate, int duration) {
		LocalDate date = LocalDate.parse(departureDate);
		return tourRepository.saveAndFlush(new Tour(theme, date, duration, 10, date.minusDays(1)));
	}

	/** 신청 인원을 정원까지 채워 마감으로 만든다. 상태 전이(#12)는 아직 없어 DB를 직접 고친다. */
	private void makeFull(Tour tour) {
		entityManager.createQuery("update Tour t set t.appliedCount = t.capacity, t.status = :full where t.id = :id")
			.setParameter("full", TourStatus.FULL)
			.setParameter("id", tour.getId())
			.executeUpdate();
		entityManager.clear();
	}

}
