package com.mwt.warehouse;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

	/** 창고 물품 전체와 현재 수량을 이름 순으로 돌려준다. */
	@Transactional(readOnly = true)
	public List<Item> getItems() {
		return itemRepository.findAll(Sort.by("name"));
	}

	/** 같은 이름의 물품이 있으면 수량만 더하고(테마는 그대로 둔다), 없으면 테마와 함께 새로 만든다. */
	@Transactional
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
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
			// 같은 이름의 새 물품을 다른 요청이 동시에 저장했다(이름 유일 제약 위반, 또는 MySQL이 교착을 풀려고 이쪽을 되돌림)
			throw new BusinessException(HttpStatus.CONFLICT, "다른 직원이 같은 물품을 방금 추가했습니다. 다시 시도해 주세요.");
		}
	}

}
