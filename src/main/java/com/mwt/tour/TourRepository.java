package com.mwt.tour;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 여행 상품을 저장하고 찾는다(설계산출물 4.4). 다른 모듈은 이 인터페이스 대신 TourService를 쓴다.
 */
interface TourRepository extends JpaRepository<Tour, Long> {

	/** 출발일이 오늘보다 뒤인 그 테마의 상품을 마감 상품까지 모두 찾는다. 출발일, 기간, ID 순이다. */
	@Query("""
			select t from Tour t
			where t.theme = :theme and t.departureDate > :today
			order by t.departureDate, t.duration, t.id""")
	List<Tour> findUpcomingTours(@Param("theme") Theme theme, @Param("today") LocalDate today);

	/** 테마와 출발일이 같은 상품을 기간, ID 순으로 찾는다. 상태와 오늘 날짜는 보지 않는다. */
	@Query("""
			select t from Tour t
			where t.theme = :theme and t.departureDate = :departureDate
			order by t.duration, t.id""")
	List<Tour> findByThemeAndDepartureDate(@Param("theme") Theme theme,
			@Param("departureDate") LocalDate departureDate);

}
