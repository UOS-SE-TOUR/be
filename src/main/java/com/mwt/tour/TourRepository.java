package com.mwt.tour;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 여행 상품을 저장하고 찾는다(설계산출물 4.4). 다른 모듈은 이 인터페이스 대신 TourService를 쓴다.
 * 잠그고 읽는 findByIdForUpdate는 TourRepositoryCustom에 있다.
 */
interface TourRepository extends JpaRepository<Tour, Long>, TourRepositoryCustom {

	/** 상태는 보지 않아 마감 상품도 들어간다. */
	@Query("""
			select t from Tour t
			where t.theme = :theme and t.departureDate > :today
			order by t.departureDate, t.duration, t.id""")
	List<Tour> findUpcomingTours(@Param("theme") Theme theme, @Param("today") LocalDate today);

	/** 오늘 날짜와 상태는 보지 않는다 — 출발일이 오늘이거나 지난 상품은 TourService.findTours가 거른다. */
	@Query("""
			select t from Tour t
			where t.theme = :theme and t.departureDate = :departureDate
			order by t.duration, t.id""")
	List<Tour> findByThemeAndDepartureDate(@Param("theme") Theme theme,
			@Param("departureDate") LocalDate departureDate);

	/**
	 * 출발일이 오늘이거나 지난 모집 중·확정·마감 상품이다. 찾은 행을 잠가, 자정 직전에 시작한 신청이 신청 인원을 더하는 사이에
	 * 예약 작업이 낡은 인원으로 취소하거나 그 인원을 덮어쓰지 않게 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select t from Tour t
			where t.status in (com.mwt.tour.TourStatus.RECRUITING, com.mwt.tour.TourStatus.CONFIRMED, com.mwt.tour.TourStatus.FULL)
			and t.departureDate <= :today
			order by t.id""")
	List<Tour> findDeparting(@Param("today") LocalDate today);

	/** 여행 종료일(출발일에 '여행 기간−1일'을 더한 날)이 오늘보다 앞인 진행 중 상품이다. */
	@Query("""
			select t from Tour t
			where t.status = com.mwt.tour.TourStatus.IN_PROGRESS
			and t.departureDate + (t.duration - 1) day < :today
			order by t.id""")
	List<Tour> findFinished(@Param("today") LocalDate today);

}
