package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

/**
 * 창고 물품 규칙(설계산출물 1.3 (7), 5.9). 스프링 없이 본다.
 */
class ItemTest {

	@Test
	void 새_물품은_이름_앞뒤_공백을_떼고_만든다() {
		Item item = new Item(Item.normalizeName("  생수 "), Theme.GOLF, 10);

		assertThat(item.getName()).isEqualTo("생수");
		assertThat(item.getTheme()).isEqualTo(Theme.GOLF);
		assertThat(item.getQuantity()).isEqualTo(10);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void 수량이_1보다_작으면_새_물품을_만들지_않는다(int quantity) {
		assertThatThrownBy(() -> new Item("생수", Theme.GOLF, quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");
	}

	@Test
	void 테마가_없으면_새_물품을_만들지_않는다() {
		assertThatThrownBy(() -> new Item("생수", null, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("테마를 골라 주세요.");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n"})
	void 이름이_비었으면_거부한다(String name) {
		assertThatThrownBy(() -> Item.normalizeName(name))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름을 입력해 주세요.");
	}

	@Test
	void 이름은_100자까지_쓸_수_있고_글자_수는_화면에_보이는_글자로_센다() {
		assertThat(Item.normalizeName("가".repeat(100))).hasSize(100);
		assertThat(Item.normalizeName("😀".repeat(100))).hasSize(200);   // Java 길이로는 200이지만 100자다

		assertThatThrownBy(() -> Item.normalizeName("가".repeat(101)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름은 100자까지 쓸 수 있습니다.");
	}

	@Test
	void 수량을_더한다() {
		Item item = new Item("생수", Theme.GOLF, 10);

		item.addQuantity(5);

		assertThat(item.getQuantity()).isEqualTo(15);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void 더할_수량이_1보다_작으면_거부하고_수량은_그대로다(int quantity) {
		Item item = new Item("생수", Theme.GOLF, 10);

		assertThatThrownBy(() -> item.addQuantity(quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");
		assertThat(item.getQuantity()).isEqualTo(10);
	}

	@Test
	void 더해서_정수_범위를_넘으면_거부하고_수량은_그대로다() {
		Item item = new Item("생수", Theme.GOLF, Integer.MAX_VALUE - 1);

		item.addQuantity(1);
		assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);

		assertThatThrownBy(() -> item.addQuantity(1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("창고에 둘 수 있는 수량을 넘습니다.");
		assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
	}

}
