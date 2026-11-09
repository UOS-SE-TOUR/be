package com.mwt.warehouse;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mwt.tour.Theme;

import lombok.RequiredArgsConstructor;

/**
 * 창고 물품 목록과 추가를 처리한다(설계산출물 4.8, MWT-UC9).
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

	static final int MAX_ATTEMPTS = 3;

	private final ItemRepository itemRepository;
	private final TransactionTemplate transactionTemplate;

	@Transactional(readOnly = true)
	public List<Item> getItems() {
		return itemRepository.findAll(Sort.by("name"));
	}

	/**
	 * 같은 이름의 물품이 있으면 수량만 더하고(테마는 그대로 둔다), 없으면 테마와 함께 새로 만든다.
	 * 같은 이름인지는 DB 정렬 규칙(utf8mb4_0900_ai_ci)으로 가려 대소문자·악센트만 다른 이름도 같은 물품이다.
	 *
	 * <p>새 물품을 넣을 때 다른 요청과 겹치면 MySQL이 이쪽을 되돌린다 — 같은 이름을 먼저 넣었으면 유일 제약 위반,
	 * 없는 이름을 잠그며 찾은 구간이 엇갈렸으면 교착이다. 그때는 새 트랜잭션에서 처음부터 다시 한다.
	 * 다시 하면 물품이 이미 있어 수량만 더하거나, 엇갈린 상대가 끝나 그대로 넣는다.
	 */
	public Item addItem(String itemName, Theme theme, int quantity) {
		String name = Item.normalizeName(itemName);
		for (int attempt = 1; ; attempt++) {
			try {
				return transactionTemplate.execute(status -> addOrCreate(name, theme, quantity));
			} catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
				if (attempt == MAX_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private Item addOrCreate(String name, Theme theme, int quantity) {
		Optional<Item> found = itemRepository.findByName(name);
		if (found.isPresent()) {
			Item item = found.get();
			item.addQuantity(quantity);
			return itemRepository.save(item);
		}
		return itemRepository.save(new Item(name, theme, quantity));
	}

}
