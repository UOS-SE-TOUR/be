package com.mwt.tour;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * findByIdForUpdate를 @Lock을 붙인 @Query로 두지 않는 이유: 이 트랜잭션에서 이미 읽은 상품이면 Hibernate가 잠그기만 하고
 * 그 낡은 객체를 그대로 돌려준다. 그 뒤 refresh를 불러도 이미 잠갔다며 잠그지 않고 다시 읽는데, MySQL(REPEATABLE READ)은
 * 잠그지 않은 읽기에 트랜잭션 처음의 값을 줘서 다른 신청이 더한 인원을 못 본다(be 실측, Hibernate 7.4 DefaultRefreshEventListener).
 * 그래서 아직 잠그지 않은 객체를 잠그며 새로 읽는다(select ... for update는 늘 최신 값을 읽는다).
 */
@RequiredArgsConstructor
class TourRepositoryCustomImpl implements TourRepositoryCustom {

	private final EntityManager entityManager;

	@Override
	public Optional<Tour> findByIdForUpdate(Long tourId) {
		Tour tour = entityManager.find(Tour.class, tourId);
		if (tour == null) {
			return Optional.empty();
		}
		// 이 트랜잭션이 이미 잠근 상품이면 그 뒤로 아무도 못 바꿨으니 메모리의 값이 최신이다. 다시 읽으면 오히려 아직 DB에 쓰지 않은 변경을 낡은 값으로 덮는다
		if (entityManager.getLockMode(tour) != LockModeType.PESSIMISTIC_WRITE) {
			entityManager.refresh(tour, LockModeType.PESSIMISTIC_WRITE);
		}
		return Optional.of(tour);
	}

}
