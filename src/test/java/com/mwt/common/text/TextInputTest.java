package com.mwt.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mwt.common.exception.BusinessException;

/**
 * 입력 글자 다듬기. 스프링 없이 본다.
 */
class TextInputTest {

	@Test
	void 자모를_풀어_쓴_한글은_NFC로_모은다() {
		String decomposed = Normalizer.normalize("비행기 이코노미석", Normalizer.Form.NFD);
		assertThat(decomposed).isNotEqualTo("비행기 이코노미석");

		assertThat(TextInput.normalize(decomposed)).isEqualTo("비행기 이코노미석");
	}

	@ParameterizedTest
	@ValueSource(strings = {"  생수 ", "\t생수\n", "\u00A0생수\u00A0", "\u200B생수\u200B", "\uFEFF생수", "\u3000생수\u2060",
			"\u00A0 \u200B생수\u202F\u200D "})
	void 앞뒤_공백은_보이지_않는_공백까지_뗀다(String value) {
		assertThat(TextInput.normalize(value)).isEqualTo("생수");
	}

	@ParameterizedTest
	@ValueSource(ints = {0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800, 0x034F, 0x200E, 0x200F, 0x202A, 0x202E, 0x2066, 0x2069,
			0x061C, 0x206F, 0x00AD, 0x2060, 0x2064, 0x180E, 0xFFF9, 0x1D173, 0xE0001, 0x0000, 0x0001, 0x007F, 0x0085})
	void 한글_채움_문자와_글자_방향_표시와_제어_문자도_보이지_않는_공백으로_뗀다(int codePoint) {
		String invisible = Character.toString(codePoint);

		assertThat(TextInput.normalize(invisible + "생수" + invisible)).isEqualTo("생수");
		assertThatThrownBy(() -> TextInput.require(invisible, "물품 이름", 100)).hasMessage("물품 이름을 입력해 주세요.");
	}

	@Test
	void 가운데_공백은_그대로_둔다() {
		assertThat(TextInput.normalize(" 비행기 이코노미석 ")).isEqualTo("비행기 이코노미석");
	}

	@Test
	void 이모지_안의_이음_글자와_끝의_모양_선택자는_떼지_않는다() {
		String family = "가족👨\u200D👩\u200D👧";   // 👨\u200D👩\u200D👧 — 가운데 이음 글자는 남는다
		String redHeart = "사랑❤\uFE0F";   // ❤\uFE0F — 이모지 모양 선택자

		assertThat(TextInput.normalize(family + " ")).isEqualTo(family);
		assertThat(TextInput.normalize(redHeart)).isEqualTo(redHeart);
	}

	@Test
	void 깃발_이모지_끝의_태그_글자는_떼지_않는다() {
		// 잉글랜드 깃발: 검은 깃발 + 태그 글자 g·b·e·n·g + 태그 끝. 태그 글자는 형식 문자지만 깃발의 일부다
		String england = new String(new int[] {0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F}, 0, 7);

		assertThat(TextInput.normalize(england + " ")).isEqualTo(england);
	}

	@Test
	void null은_null로_둔다() {
		assertThat(TextInput.normalize(null)).isNull();
	}

	@Test
	void 다듬은_값을_돌려준다() {
		assertThat(TextInput.require(" \u00A0" + Normalizer.normalize("생수", Normalizer.Form.NFD) + "\u200B", "물품 이름", 100))
			.isEqualTo("생수");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t\n", "\u00A0", "\u200B", "\u00A0\u200B\uFEFF "})
	void 비었거나_공백만_있으면_거부한다(String value) {
		assertThatThrownBy(() -> TextInput.require(value, "물품 이름", 100))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름을 입력해 주세요.");
	}

	@Test
	void 최대_글자_수까지_받고_넘으면_거부한다() {
		assertThat(TextInput.require("가".repeat(100), "물품 이름", 100)).hasSize(100);

		assertThatThrownBy(() -> TextInput.require("가".repeat(101), "물품 이름", 100))
			.isInstanceOf(BusinessException.class)
			.hasMessage("물품 이름은 100자까지 쓸 수 있습니다.");
	}

	@Test
	void 앞뒤_공백은_글자_수에_들지_않는다() {
		assertThat(TextInput.require(" " + "가".repeat(100) + "\u00A0", "물품 이름", 100)).hasSize(100);
	}

	@Test
	void 자모를_풀어_쓴_한글은_모은_음절로_센다() {
		String decomposed = Normalizer.normalize("가".repeat(100), Normalizer.Form.NFD);
		assertThat(decomposed).hasSize(200);

		assertThat(TextInput.require(decomposed, "물품 이름", 100)).isEqualTo("가".repeat(100));
	}

	@Test
	void 이모지는_한_글자로_센다() {
		String emojis = "🚌".repeat(100);
		assertThat(emojis).hasSize(200);   // Java 길이로는 200이지만 코드포인트로는 100자다

		assertThat(TextInput.length(emojis)).isEqualTo(100);
		assertThat(TextInput.require(emojis, "교통 이름", 100)).isEqualTo(emojis);
		assertThatThrownBy(() -> TextInput.require("🚌".repeat(101), "교통 이름", 100))
			.isInstanceOf(BusinessException.class)
			.hasMessage("교통 이름은 100자까지 쓸 수 있습니다.");
	}

	@Test
	void 항목_이름의_받침에_맞춰_조사를_고른다() {
		assertThatThrownBy(() -> TextInput.require("", "아이디", 50)).hasMessage("아이디를 입력해 주세요.");
		assertThatThrownBy(() -> TextInput.require("a".repeat(51), "아이디", 50)).hasMessage("아이디는 50자까지 쓸 수 있습니다.");
		assertThatThrownBy(() -> TextInput.require("", "연락처", 20)).hasMessage("연락처를 입력해 주세요.");
		assertThatThrownBy(() -> TextInput.require("", "이름", 50)).hasMessage("이름을 입력해 주세요.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "ID", "이름 "})
	void 항목_이름이_한글_음절로_끝나지_않으면_조사를_고를_수_없어_입력이_맞아도_개발자_오류다(String label) {
		assertThatThrownBy(() -> TextInput.require("", label, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TextInput.require("생수", label, 10)).isInstanceOf(IllegalArgumentException.class);
	}

}
