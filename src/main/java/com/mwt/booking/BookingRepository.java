package com.mwt.booking;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 신청을 저장하고 찾는다(설계산출물 4.5). 이전 여행은 완료(COMPLETED) 상태인 상품의 신청이고, 횟수는 신청 건수로 센다(가정).
 */
interface BookingRepository extends JpaRepository<Booking, Long> {

	/** 출발일이 최근인 순서다. 출발일이 같으면 나중에 신청한 것이 앞이다. */
	@Query("""
			select b from Booking b join fetch b.tour t
			where b.member.id = :memberId and t.status = com.mwt.tour.TourStatus.COMPLETED
			order by t.departureDate desc, b.id desc""")
	List<Booking> findPreviousBookings(@Param("memberId") Long memberId);

	@Query("""
			select count(b) from Booking b
			where b.member.id = :memberId and b.tour.status = com.mwt.tour.TourStatus.COMPLETED""")
	long countPreviousBookings(@Param("memberId") Long memberId);

}
