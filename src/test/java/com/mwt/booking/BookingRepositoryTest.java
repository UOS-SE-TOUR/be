package com.mwt.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.tour.Theme;
import com.mwt.tour.Tour;
import com.mwt.tour.TourStatus;

import jakarta.persistence.EntityManager;

/**
 * 이전 여행 찾기·세기 쿼리(설계산출물 4.5 BookingRepository)를 MySQL(Testcontainers)에서 본다. 테스트마다 되돌린다.
 * 이전 여행은 완료(COMPLETED) 상태인 상품의 신청이다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@DataJpaTest
class BookingRepositoryTest {

	@Autowired
	BookingRepository bookingRepository;

	@Autowired
	EntityManager entityManager;

	@Test
	void 이전_여행은_그_회원의_완료_상태인_상품의_신청만_출발일이_최근인_순서로_찾고_센다() {
		long me = member("me");
		long other = member("other");
		long older = tour("2026-09-01", TourStatus.COMPLETED);
		long newer = tour("2026-10-01", TourStatus.COMPLETED);
		long olderBooking = booking(me, older);
		long newerBooking = booking(me, newer);
		booking(me, tour("2026-11-08", TourStatus.IN_PROGRESS));
		booking(me, tour("2026-12-01", TourStatus.CONFIRMED));
		booking(me, tour("2026-12-02", TourStatus.RECRUITING));
		booking(me, tour("2026-12-03", TourStatus.FULL));
		booking(me, tour("2026-08-01", TourStatus.CANCELLED));
		booking(other, newer);
		entityManager.clear();

		List<Booking> previous = bookingRepository.findPreviousBookings(me);

		assertThat(previous).extracting(Booking::getId).containsExactly(newerBooking, olderBooking);
		assertThat(Hibernate.isInitialized(previous.getFirst().getTour())).isTrue();
		assertThat(bookingRepository.countPreviousBookings(me)).isEqualTo(2);
	}

	@Test
	void 같은_상품을_두_번_신청했으면_두_건이고_나중_신청이_앞이다() {
		long me = member("me");
		long completed = tour("2026-10-01", TourStatus.COMPLETED);
		long first = booking(me, completed);
		long second = booking(me, completed);
		entityManager.clear();

		assertThat(bookingRepository.findPreviousBookings(me)).extracting(Booking::getId).containsExactly(second, first);
		assertThat(bookingRepository.countPreviousBookings(me)).isEqualTo(2);
	}

	@Test
	void 이전_여행이_없으면_빈_목록과_0건() {
		long me = member("me");
		booking(me, tour("2026-12-01", TourStatus.CONFIRMED));
		entityManager.clear();

		assertThat(bookingRepository.findPreviousBookings(me)).isEmpty();
		assertThat(bookingRepository.countPreviousBookings(me)).isZero();
	}

	/** 회원 저장(member 모듈의 #9)이 아직 없어 행을 직접 넣는다. */
	private long member(String loginId) {
		entityManager.createNativeQuery("""
				insert into member (login_id, password, name, address, phone, member_type)
				values (?, 'encoded', '고객', '서울', '01012345678', 'CUSTOMER')""")
			.setParameter(1, loginId)
			.executeUpdate();
		return lastInsertId();
	}

	/** 출발일 하루 전을 "오늘"로 넣어 만들고, 상태는 직접 고친다(상태 전이 메소드는 tour 패키지 안에서만 부를 수 있다). */
	private long tour(String departureDate, TourStatus status) {
		LocalDate date = LocalDate.parse(departureDate);
		Tour tour = new Tour(Theme.GOLF, date, 5, 10, date.minusDays(1));
		entityManager.persist(tour);
		entityManager.flush();
		entityManager.createQuery("update Tour t set t.status = :status where t.id = :id")
			.setParameter("status", status)
			.setParameter("id", tour.getId())
			.executeUpdate();
		return tour.getId();
	}

	/** 신청 저장(#17)이 아직 없어 행을 직접 넣는다. */
	private long booking(long memberId, long tourId) {
		entityManager.createNativeQuery("""
				insert into booking (member_id, tour_id, level, headcount, total_price, discount, applied_at)
				values (?, ?, 'GRAND', 2, 3000000, 0, '2026-08-01 00:00:00')""")
			.setParameter(1, memberId)
			.setParameter(2, tourId)
			.executeUpdate();
		return lastInsertId();
	}

	private long lastInsertId() {
		return ((Number) entityManager.createNativeQuery("select last_insert_id()").getSingleResult()).longValue();
	}

}
