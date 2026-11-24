package com.mwt.warehouse;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;
import com.mwt.tour.Tour;

import lombok.RequiredArgsConstructor;

/**
 * 창고 물품 목록, 등록, 수량 더하기, 1인당 개수 바꾸기, 확정 때 줄이기를 처리하는 warehouse 모듈의 공개 Service다
 * (설계산출물 4.8, MWT-UC4·UC9). 다른 모듈은 창고 물품의 수량을 이 클래스로만 바꾼다.
 * 수량을 바꿀 때마다 같은 트랜잭션에서 기록(ItemHistory)을 남긴다. 늦은 취소로 되돌리기(putBackItems)도 여기서 한다.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

	private static final String DUPLICATE_ITEM = "이미 있는 물품입니다. 목록에서 수량을 더해 주세요.";
	private static final String NAME_THEME_UNIQUE_KEY = "uk_item_name_theme";   // V2__item_unique_name_theme.sql

	private final ItemRepository itemRepository;
	private final ItemHistoryRepository itemHistoryRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<Item> getItems() {
		return itemRepository.findAll(Sort.by("name", "theme"));
	}

	/**
	 * 새 물품을 등록한다. 이름과 테마가 같은 물품이 이미 있으면 400이다 — 있는 물품에는 addQuantity로 더한다.
	 * 같은 이름도 테마가 다르면 다른 물품이다. 같은 이름인지는 V3 마이그레이션이 정한 정렬 규칙(utf8mb4_0900_ai_ci)으로 가려
	 * 대소문자·악센트만 다른 이름도 같은 이름이다.
	 *
	 * <p>있는지는 잠그지 않고 먼저 본다(ItemRepository.existsByNameAndTheme). 그사이 다른 요청이 같은 새 물품을 먼저 넣었으면
	 * 저장할 때 (이름, 테마) 유일 제약에 걸리므로 같은 400으로 바꾼다. 다른 무결성 오류는 서버 잘못이라 그대로 던진다.
	 */
	@Transactional
	public Item registerItem(String itemName, Theme theme, int perPerson, int quantity) {
		Item item = new Item(itemName, theme, perPerson, quantity);
		if (itemRepository.existsByNameAndTheme(item.getName(), item.getTheme())) {
			throw new BusinessException(DUPLICATE_ITEM);
		}
		try {
			Item saved = itemRepository.saveAndFlush(item);
			record(saved, ItemHistoryReason.REGISTER, null, saved.getQuantity());
			return saved;
		} catch (DataIntegrityViolationException e) {
			if (isDuplicateItem(e)) {
				throw new BusinessException(DUPLICATE_ITEM);
			}
			throw e;
		}
	}

	/** (이름, 테마) 유일 키(uk_item_name_theme) 위반인지. MemberService.isLoginIdTaken과 같은 방법으로 가린다. */
	static boolean isDuplicateItem(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
				return violation.getConstraintName().toLowerCase(Locale.ROOT).endsWith(NAME_THEME_UNIQUE_KEY);
			}
		}
		return false;
	}

	/** 있는 물품에 수량을 더한다. 물품 행을 잠그며 읽으므로 동시에 더하거나 확정 때 줄여도 한쪽 수량이 사라지지 않는다. */
	@Transactional
	public Item addQuantity(Long itemId, int quantity) {
		Item item = findForUpdate(itemId);
		item.addQuantity(quantity);
		record(item, ItemHistoryReason.ADD, null, quantity);
		return item;
	}

	/** 1인당 개수를 바꾼다. 이미 줄인 수량은 다시 계산하지 않고, 이 뒤에 줄이는 것부터 새 값을 쓴다. */
	@Transactional
	public Item changePerPerson(Long itemId, int perPerson) {
		Item item = findForUpdate(itemId);
		item.changePerPerson(perPerson);
		return item;
	}

	/**
	 * 상품이 확정될 때 그 테마 물품마다 수량을 "인원 × 1인당 개수"만큼 줄인다(booking 모듈이 신청·직원 확정 때 같은 트랜잭션에서 부른다).
	 * 줄인 물품마다 그 상품을 붙여 기록한다. 늦은 취소로 되돌릴 양은 이 기록으로 정한다.
	 * 1인당 개수가 0인 물품은 그대로이고, 모자라도 줄여 수량이 음수가 된다. 그 테마 물품이 없으면 아무것도 줄지 않는다.
	 * 그 테마 물품 행을 잠그고 줄이므로 직원이 같은 때 수량을 더해도 한쪽이 사라지지 않는다.
	 * <p>부르는 쪽 트랜잭션 밖에서 부르면 바로 실패한다(MANDATORY) — 따로 커밋되면 신청이 되돌려져도 수량은 줄어 있어서다.
	 * 부르는 쪽 트랜잭션은 그 전에 창고 물품을 읽어 두지 않는다. 먼저 읽어 둔 물품이면 낡은 수량에서 줄인다(ItemRepository 머리말).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void takeOutItems(Tour tour, int headcount) {
		for (Item item : itemRepository.findByThemeForUpdate(tour.getTheme().name())) {
			int before = item.getQuantity();
			item.takeOut(headcount);
			if (item.getQuantity() != before) {
				record(item, ItemHistoryReason.TAKE_OUT, tour, item.getQuantity() - before);
			}
		}
	}

	/**
	 * 늦은 취소한 상품 때문에 줄였던 물품을 되돌린다(booking 모듈이 늦은 취소와 같은 트랜잭션에서 부른다). 되돌릴 양은 그 상품의 기록에서
	 * 줄인 양이라, 줄인 뒤 1인당 개수를 바꿨거나 물품을 새로 등록했어도 줄인 만큼만 돌아간다. 지운 물품과 이 기록이 생기기 전에 줄인 수량은 되돌리지 않는다.
	 * 확정 때 줄이기와 같은 순서로 그 테마 물품을 잠그므로 서로 기다리다 교착하지 않는다. 부르는 쪽 트랜잭션 밖에서 부르면 바로 실패한다(MANDATORY).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void putBackItems(Tour tour) {
		List<Item> items = itemRepository.findByThemeForUpdate(tour.getTheme().name());
		Map<Long, Integer> changed = itemHistoryRepository.findByTourId(tour.getId()).stream()
			.collect(Collectors.groupingBy(ItemHistory::getItemId, Collectors.summingInt(ItemHistory::getChangeAmount)));
		for (Item item : items) {
			int taken = -changed.getOrDefault(item.getId(), 0);
			if (taken > 0) {
				item.putBack(taken);
				record(item, ItemHistoryReason.PUT_BACK, tour, taken);
			}
		}
	}

	/** 최근 기록부터 200줄이다. */
	@Transactional(readOnly = true)
	public List<ItemHistory> getHistory() {
		return itemHistoryRepository.findTop200ByOrderByIdDesc();
	}

	private void record(Item item, ItemHistoryReason reason, Tour tour, int changeAmount) {
		itemHistoryRepository.save(new ItemHistory(item, reason, tour, changeAmount, clock.instant()));
	}

	/** 없는 물품이면 404다. */
	private Item findForUpdate(Long itemId) {
		return itemRepository.findByIdForUpdate(itemId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "창고 물품을 찾을 수 없습니다."));
	}

}
