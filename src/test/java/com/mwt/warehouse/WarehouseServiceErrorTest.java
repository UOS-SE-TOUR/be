package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.mwt.tour.Theme;

/**
 * 새 물품 저장이 DB 무결성 오류로 실패했을 때, 이름 유일 제약이 아니면 409로 바꾸지 않는다. DB 없이 본다.
 */
class WarehouseServiceErrorTest {

	private final ItemRepository itemRepository = mock(ItemRepository.class);
	private final WarehouseService warehouseService = new WarehouseService(itemRepository);

	@Test
	void 이름_유일_제약이_아닌_무결성_오류는_그대로_던진다() {
		DataIntegrityViolationException tooLong = new DataIntegrityViolationException("Data too long",
				new DataException("Data too long for column 'name'", new SQLException("Data too long", "22001", 1406)));
		given(itemRepository.findByName("생수")).willReturn(Optional.empty());
		given(itemRepository.save(any(Item.class))).willThrow(tooLong);

		assertThatThrownBy(() -> warehouseService.addItem("생수", Theme.GOLF, 1)).isSameAs(tooLong);
	}

}
