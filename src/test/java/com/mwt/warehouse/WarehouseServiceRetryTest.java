package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

/**
 * 새 물품 저장이 다른 요청과 겹쳐 되돌려졌을 때 다시 하는 횟수. DB 없이 본다(실제 겹침은 WarehouseServiceTest).
 */
class WarehouseServiceRetryTest {

	private final ItemRepository itemRepository = mock(ItemRepository.class);
	private final WarehouseService warehouseService = new WarehouseService(itemRepository,
			mock(PlatformTransactionManager.class));

	@Test
	void 세_번_모두_겹치면_마지막_예외를_그대로_던진다() {
		DataIntegrityViolationException duplicate = new DataIntegrityViolationException("Duplicate entry");
		CannotAcquireLockException deadlock = new CannotAcquireLockException("Deadlock found");
		given(itemRepository.existsByNameAndTheme("생수", Theme.GOLF)).willReturn(false);
		given(itemRepository.save(any(Item.class))).willThrow(deadlock, duplicate, deadlock);

		assertThatThrownBy(() -> warehouseService.addItem("생수", Theme.GOLF, 1)).isSameAs(deadlock);
		verify(itemRepository, times(WarehouseService.MAX_ATTEMPTS)).save(any(Item.class));
	}

	@Test
	void 수량_규칙_위반은_다시_하지_않는다() {
		given(itemRepository.existsByNameAndTheme("생수", Theme.GOLF)).willReturn(true);
		given(itemRepository.findByNameAndTheme("생수", Theme.GOLF)).willReturn(Optional.of(new Item("생수", Theme.GOLF, 10)));

		assertThatThrownBy(() -> warehouseService.addItem("생수", Theme.GOLF, 0))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");
		verify(itemRepository, times(1)).findByNameAndTheme("생수", Theme.GOLF);
	}

}
