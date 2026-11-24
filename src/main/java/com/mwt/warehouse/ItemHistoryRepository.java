package com.mwt.warehouse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

/**
 * 창고 물품 수량 기록을 저장하고 찾는다. 다른 모듈은 이 인터페이스 대신 WarehouseService를 쓴다.
 */
interface ItemHistoryRepository extends JpaRepository<ItemHistory, Long> {

	/** 최근 기록부터 200줄이다. */
	List<ItemHistory> findTop200ByOrderByIdDesc();

	/**
	 * 그 상품 때문에 줄이거나 되돌린 기록이다. 늦은 취소로 되돌릴 양을 정할 때 쓴다.
	 * 잠그며(공유 잠금) 읽어 늘 최신 값을 본다. 잠그지 않고 읽으면 MySQL이 트랜잭션 첫 읽기 때의 값을 줘서,
	 * 늦은 취소가 상품 잠금을 기다리는 사이 커밋된 신청의 줄임 기록이 빠지고 그만큼 덜 되돌린다(BookingRepository.findByTourId와 같은 이유).
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	List<ItemHistory> findByTourId(Long tourId);

}
