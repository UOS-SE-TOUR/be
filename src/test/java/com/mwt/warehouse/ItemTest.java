package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

/**
 * 창고 물품 규칙(설계산출물 1.3 (7), 5.9, be #71). 스프링 없이 본다.
 */
class ItemTest {

	private static final String PER_PERSON_RANGE = "1인당 개수는 0개부터 100개까지입니다.";

	@Test
	void 새_물품은_이름_앞뒤_공백을_떼고_만든다() {
		Item item = new Item("  생수 ", Theme.GOLF, 2, 10);

		assertThat(item.getName()).isEqualTo("생수");
		assertThat(item.getTheme()).isEqualTo(Theme.GOLF);
		assertThat(item.getPerPerson()).isEqualTo(2);
		assertThat(item.getQuantity()).isEqualTo(10);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void 수량이_1보다_작으면_새_물품을_만들지_않는다(int quantity) {
		assertThatThrownBy(() -> new Item("생수", Theme.GOLF, 1, quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");
	}

	@Test
	void 테마가_없으면_새_물품을_만들지_않는다() {
		assertThatThrownBy(() -> new Item("생수", null, 1, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("테마를 골라 주세요.");
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 100})
	void 일인당_개수는_0개부터_100개까지_받는다(int perPerson) {
		Item item = new Item("생수", Theme.GOLF, perPerson, 1);
		assertThat(item.getPerPerson()).isEqualTo(perPerson);

		item.changePerPerson(5);
		item.changePerPerson(perPerson);
		assertThat(item.getPerPerson()).isEqualTo(perPerson);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
	void 일인당_개수가_범위_밖이면_만들지도_바꾸지도_않는다(int perPerson) {
		assertThatThrownBy(() -> new Item("생수", Theme.GOLF, perPerson, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage(PER_PERSON_RANGE);

		Item item = new Item("생수", Theme.GOLF, 3, 1);
		assertThatThrownBy(() -> item.changePerPerson(perPerson))
			.isInstanceOf(BusinessException.class)
			.hasMessage(PER_PERSON_RANGE);
		assertThat(item.getPerPerson()).isEqualTo(3);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n", " ", "​"})
	void 이름이_비었으면_거부한다(String name) {
		assertThatThrownBy(() -> Item.normalizeName(name))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름을 입력해 주세요.");
	}

	@Test
	void 이름은_100자까지이고_글자_수는_DB처럼_유니코드_코드포인트로_센다() {
		assertThat(Item.normalizeName("가".repeat(100))).hasSize(100);
		assertThat(Item.normalizeName("😀".repeat(100))).hasSize(200);   // Java 길이로는 200이지만 코드포인트로는 100자다

		assertThatThrownBy(() -> Item.normalizeName("가".repeat(101)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름은 100자까지 쓸 수 있습니다.");
	}

	@Test
	void 자모를_풀어_쓴_한글_이름은_NFC로_모아_같은_이름이_되고_음절로_센다() {
		String decomposed = Normalizer.normalize("생수", Normalizer.Form.NFD);   // ㅅㅐㅇㅅㅜ 다섯 코드포인트
		assertThat(decomposed).hasSize(5);

		assertThat(Item.normalizeName(decomposed)).isEqualTo("생수");
		assertThat(Item.normalizeName(Normalizer.normalize("가".repeat(100), Normalizer.Form.NFD))).hasSize(100);
	}

	@Test
	void 수량을_더한다() {
		Item item = new Item("생수", Theme.GOLF, 1, 10);

		item.addQuantity(5);

		assertThat(item.getQuantity()).isEqualTo(15);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void 더할_수량이_1보다_작으면_거부하고_수량은_그대로다(int quantity) {
		Item item = new Item("생수", Theme.GOLF, 1, 10);

		assertThatThrownBy(() -> item.addQuantity(quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");
		assertThat(item.getQuantity()).isEqualTo(10);
	}

	@Test
	void 더해서_정수_범위를_넘으면_거부하고_수량은_그대로다() {
		Item item = new Item("생수", Theme.GOLF, 1, Integer.MAX_VALUE - 1);

		item.addQuantity(1);
		assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);

		assertThatThrownBy(() -> item.addQuantity(1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("창고에 둘 수 있는 수량을 넘습니다.");
		assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
	}

	@ParameterizedTest(name = "1인당 {0}개, {1}명 → {2}")
	@CsvSource({"1, 3, 7", "2, 3, 4", "3, 4, -2", "0, 5, 10"})
	void 확정되면_인원_곱하기_1인당_개수만큼_줄이고_모자라면_음수가_된다(int perPerson, int headcount, int left) {
		Item item = new Item("생수", Theme.GOLF, perPerson, 10);

		item.takeOut(headcount);

		assertThat(item.getQuantity()).isEqualTo(left);
	}

	@Test
	void 음수_수량에도_더하기가_맞게_된다() {
		Item item = new Item("생수", Theme.GOLF, 1, 1);
		item.takeOut(2);   // -1
		assertThat(item.getQuantity()).isEqualTo(-1);

		item.addQuantity(Integer.MAX_VALUE);   // 예전 넘침 검사(MAX − 수량)는 수량이 음수면 넘쳐 이 요청을 거부했다

		assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE - 1);
	}

	@Test
	void 줄여서_정수_범위를_벗어나면_거부하고_수량은_그대로다() {
		Item item = new Item("생수", Theme.GOLF, 100, 1);
		item.takeOut(Integer.MAX_VALUE / 100);   // 1 − 21474836 × 100

		int before = item.getQuantity();
		assertThatThrownBy(() -> item.takeOut(Integer.MAX_VALUE))
			.isInstanceOf(BusinessException.class)
			.hasMessage("기념품 수량을 줄일 수 없어 신청할 수 없습니다.");
		assertThat(item.getQuantity()).isEqualTo(before);
	}

}
