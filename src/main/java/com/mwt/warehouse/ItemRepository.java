package com.mwt.warehouse;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.mwt.tour.Theme;

import jakarta.persistence.LockModeType;

/**
 * 창고 물품을 저장하고 찾는다(설계산출물 4.8). 물품은 이름과 테마로 찾는다.
 */
interface ItemRepository extends JpaRepository<Item, Long> {

	/** 잠그지 않고 본다. 없는 물품을 잠그며 찾으면 MySQL이 그 물품이 들어갈 구간까지 잠가, 다른 새 물품끼리도 교착한다. */
	boolean existsByNameAndTheme(String name, Theme theme);

	/** 수량을 더하려고 찾으므로 찾은 행을 잠근다. 동시에 같은 물품을 더해도 한쪽 수량이 사라지지 않는다. 있는 물품에만 쓴다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Item> findByNameAndTheme(String name, Theme theme);

}
