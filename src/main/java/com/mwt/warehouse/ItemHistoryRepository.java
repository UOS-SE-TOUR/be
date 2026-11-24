package com.mwt.warehouse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 창고 물품 수량 기록을 저장하고 찾는다. 다른 모듈은 이 인터페이스 대신 WarehouseService를 쓴다.
 */
interface ItemHistoryRepository extends JpaRepository<ItemHistory, Long> {

	/** 최근 기록부터 200줄이다. */
	List<ItemHistory> findTop200ByOrderByIdDesc();

	/** 그 상품 때문에 줄이거나 되돌린 기록이다. 늦은 취소로 되돌릴 양을 정할 때 쓴다. */
	List<ItemHistory> findByTourId(Long tourId);

}
