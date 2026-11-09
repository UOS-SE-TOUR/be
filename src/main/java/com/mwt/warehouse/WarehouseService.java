package com.mwt.warehouse;

import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

import lombok.RequiredArgsConstructor;

/**
 * 창고 물품 목록과 추가를 처리한다(설계산출물 4.8, MWT-UC9).
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

	private final ItemRepository itemRepository;

	@Transactional(readOnly = true)
	public List<Item> getItems() {
		return itemRepository.findAll(Sort.by("name"));
	}

	/**
	 * 같은 이름의 물품이 있으면 수량만 더하고(테마는 그대로 둔다), 없으면 테마와 함께 새로 만든다.
	 * 같은 이름인지는 DB 정렬 규칙(utf8mb4_0900_ai_ci)으로 가려 대소문자·악센트만 다른 이름도 같은 물품이다.
	 *
	 * <p>READ COMMITTED로 두는 이유: MySQL 기본(REPEATABLE READ)에서는 없는 이름을 잠그며 찾을 때 그 사이 구간까지 잠가,
	 * 이름이 다른 새 물품 둘을 동시에 넣어도 서로 막혀 한쪽이 되돌려진다. 있는 물품은 이 수준에서도 행을 잠가 차례로 더한다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Item addItem(String itemName, Theme theme, int quantity) {
		String name = Item.normalizeName(itemName);
		Optional<Item> found = itemRepository.findByName(name);
		if (found.isPresent()) {
			Item item = found.get();
			item.addQuantity(quantity);
			return itemRepository.save(item);
		}
		try {
			return itemRepository.save(new Item(name, theme, quantity));
		} catch (DataIntegrityViolationException e) {
			if (!isUniqueViolation(e)) {
				throw e;
			}
			// 같은 이름의 새 물품을 다른 요청이 먼저 저장했다(item에 걸린 유일 제약은 이름 하나뿐이다)
			throw new BusinessException(HttpStatus.CONFLICT, "다른 직원이 같은 물품을 방금 추가했습니다. 다시 시도해 주세요.");
		}
	}

	/** 스프링은 JPA 경로의 중복 키를 DuplicateKeyException으로 바꾸지 않으므로(be 실측) Hibernate가 가린 제약 종류를 본다. */
	private static boolean isUniqueViolation(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
	}

}
