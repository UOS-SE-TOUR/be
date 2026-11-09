package com.mwt.tour;

import java.util.Optional;

/**
 * 쿼리 메소드로 쓸 수 없는 TourRepository의 상품 찾기다. 구현은 TourRepositoryCustomImpl에 있다.
 */
interface TourRepositoryCustom {

	/**
	 * 신청 인원을 더하려고 상품 행을 잠그고 최신 값으로 읽는다(설계산출물 4.4). 같은 상품의 다른 신청은 이 트랜잭션이 끝날 때까지 기다린다.
	 * 같은 트랜잭션에서 먼저 읽어 둔 상품이어도 다른 신청이 그사이 더한 인원까지 본다.
	 */
	Optional<Tour> findByIdForUpdate(Long tourId);

}
