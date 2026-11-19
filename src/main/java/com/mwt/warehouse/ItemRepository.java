package com.mwt.warehouse;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mwt.tour.Theme;

import jakarta.persistence.LockModeType;

/**
 * 창고 물품을 저장하고 찾는다(설계산출물 4.8). 다른 모듈은 이 인터페이스 대신 WarehouseService를 쓴다.
 *
 * <p>잠그며 찾는 두 메소드는 그 트랜잭션에서 물품을 처음 읽을 때 쓴다. 잠그지 않고 먼저 읽어 둔 물품이면 Hibernate가 잠그기만 하고
 * 그 낡은 객체를 돌려줘, 다른 요청이 바꾼 수량을 덮어쓴다(TourRepositoryCustomImpl의 같은 함정).
 */
interface ItemRepository extends JpaRepository<Item, Long> {

	/** 잠그지 않고 본다. 없는 물품을 잠그며 찾으면 MySQL이 그 물품이 들어갈 구간까지 잠가, 다른 새 물품끼리도 교착한다. */
	boolean existsByNameAndTheme(String name, Theme theme);

	/** 수량을 더하거나 1인당 개수를 바꾸려고 찾으므로 그 행을 잠근다. 동시에 더해도 한쪽 수량이 사라지지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Item i where i.id = :id")
	Optional<Item> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 상품이 확정되어 수량을 줄이려고 그 테마 물품을 모두 ID 순으로 잠근다(1인당 개수가 0인 물품도 — REPEATABLE READ는 훑은 행을
	 * 조건에 맞지 않아도 잠근 채 두므로 조건을 더해도 덜 잠그지 않는다).
	 *
	 * <p>늘 테마 색인(idx_item_theme)으로 찾게 한다(FORCE INDEX). 물품 대부분이 한 테마면 MySQL이 테이블 전체를 훑기로 골라 다른 테마 물품까지
	 * 잠그고, 신청마다 고른 방법이 다르면 그 사이에 낀 새 물품 등록과 셋이 서로 기다리다 교착할 수 있어서다.
	 * 네이티브 쿼리라 @Lock 대신 for update를 직접 쓰고, 테마는 이름(Theme.name())으로 받는다.
	 */
	@Query(value = "select * from item force index (idx_item_theme) where theme = :theme order by id for update",
			nativeQuery = true)
	List<Item> findByThemeForUpdate(@Param("theme") String themeName);

}
