package com.mwt.warehouse;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mwt.tour.Theme;

/**
 * 창고 물품 목록과 추가를 처리한다(설계산출물 4.8, MWT-UC9).
 */
@Service
public class WarehouseService {

	static final int MAX_ATTEMPTS = 3;

	private final ItemRepository itemRepository;
	private final TransactionTemplate transactionTemplate;

	public WarehouseService(ItemRepository itemRepository, PlatformTransactionManager transactionManager) {
		this.itemRepository = itemRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public List<Item> getItems() {
		return itemRepository.findAll(Sort.by("name"));
	}

	/**
	 * 같은 이름의 물품이 있으면 수량만 더하고, 없으면 테마와 함께 새로 만든다. 있는 물품이면 테마는 보내지 않아도 되지만,
	 * 보냈는데 그 물품의 테마와 다르면 거부한다.
	 * 같은 이름인지는 DB 정렬 규칙(utf8mb4_0900_ai_ci)으로 가려 대소문자·악센트만 다른 이름도 같은 물품이다.
	 *
	 * <p>같은 새 이름을 다른 요청이 먼저 넣었으면 유일 제약 위반으로 되돌려진다. 그때는 처음부터 다시 해 그 물품에 수량을 더한다.
	 * 다시 하려면 시도마다 자기 트랜잭션이 있어야 하므로, 트랜잭션 밖(Controller)에서만 부른다.
	 */
	public Item addItem(String itemName, Theme theme, int quantity) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("addItem은 트랜잭션 밖에서 불러야 한다(겹치면 새 트랜잭션에서 다시 한다)");
		}
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

	/** 있는지는 잠그지 않고 먼저 본다(ItemRepository.existsByName). 있을 때만 그 행을 잠그며 읽는다. */
	private Item addOrCreate(String name, Theme theme, int quantity) {
		Optional<Item> found = itemRepository.existsByName(name) ? itemRepository.findByName(name) : Optional.empty();
		if (found.isPresent()) {
			Item item = found.get();
			item.checkTheme(theme);
			item.addQuantity(quantity);
			return itemRepository.save(item);
		}
		return itemRepository.save(new Item(name, theme, quantity));
	}

}
