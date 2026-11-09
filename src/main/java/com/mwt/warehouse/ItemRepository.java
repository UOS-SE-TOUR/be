package com.mwt.warehouse;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

/**
 * 창고 물품을 저장하고 찾는다(설계산출물 4.8).
 */
interface ItemRepository extends JpaRepository<Item, Long> {

	/** 수량을 더하려고 찾으므로 행을 잠근다. 동시에 같은 물품을 더해도 한쪽 수량이 사라지지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Item> findByName(String name);

}
