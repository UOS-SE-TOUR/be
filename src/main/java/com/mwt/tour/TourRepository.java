package com.mwt.tour;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 여행 상품을 저장하고 찾는다(설계산출물 4.4). findById와 save는 JpaRepository에서 물려받는다.
 * 다른 모듈은 이 인터페이스 대신 TourService를 쓴다.
 */
interface TourRepository extends JpaRepository<Tour, Long> {

}
