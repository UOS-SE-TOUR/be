package com.mwt.booking;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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

	/**
	 * 그 상품의 신청 모두를 신청한 순서로 돌려준다. 확정 문자를 받을 연락처를 읽도록 회원을 함께 가져온다.
	 * 잠그며 읽는다(for share) — MySQL(REPEATABLE READ)은 잠그지 않은 읽기에 트랜잭션 첫 읽기 때의 값을 줘서,
	 * 상품 잠금을 기다리는 사이 커밋된 다른 신청이 빠진다. 잠그는 읽기는 늘 최신 값을 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("""
			select b from Booking b join fetch b.member
			where b.tour.id = :tourId
			order by b.id""")
	List<Booking> findByTourId(@Param("tourId") Long tourId);

	/** 회원과 신청 키로 신청을 찾는다. 같은 회원의 신청 키는 겹치지 않으므로 많아야 하나다. */
	Optional<Booking> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

}
