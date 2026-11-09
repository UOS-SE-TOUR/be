package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mwt.common.exception.BusinessException;

/**
 * 테마 규칙 셋(설계산출물 4.4 Theme): 등급 허용, 허니문 인원, 최소 호텔 등급.
 */
class ThemeTest {

	@ParameterizedTest
	@EnumSource(value = Theme.class, names = {"HONEYMOON", "PARENTS"})
	void 허니문과_효도_투어는_그랜드와_프리미엄만(Theme theme) {
		assertThat(theme.allows(TourLevel.CLASSIC)).isFalse();
		assertThat(theme.allows(TourLevel.GRAND)).isTrue();
		assertThat(theme.allows(TourLevel.PREMIUM)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = Theme.class, names = {"GOLF", "TREKKING"})
	void 골프와_트레킹은_세_등급_모두(Theme theme) {
		assertThat(theme.allows(TourLevel.CLASSIC)).isTrue();
		assertThat(theme.allows(TourLevel.GRAND)).isTrue();
		assertThat(theme.allows(TourLevel.PREMIUM)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(Theme.class)
	void 등급이_없으면_허용하지_않는다(Theme theme) {
		assertThat(theme.allows(null)).isFalse();
	}

	@Test
	void 허니문은_2명만_신청한다() {
		assertThatCode(() -> Theme.HONEYMOON.checkHeadcount(2)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 3, 4})
	void 허니문에_2명이_아니면_거부(int headcount) {
		assertThatThrownBy(() -> Theme.HONEYMOON.checkHeadcount(headcount))
			.isInstanceOf(BusinessException.class)
			.hasMessage("허니문 낭만 투어는 2명만 신청할 수 있습니다.");
	}

	@ParameterizedTest
	@EnumSource(value = Theme.class, names = {"PARENTS", "GOLF", "TREKKING"})
	void 허니문이_아니면_인원_제한이_없다(Theme theme) {
		assertThatCode(() -> theme.checkHeadcount(1)).doesNotThrowAnyException();
		assertThatCode(() -> theme.checkHeadcount(5)).doesNotThrowAnyException();
	}

	@Test
	void 허니문과_효도_투어는_호텔_4등급_이상() {
		assertThat(Theme.HONEYMOON.minHotelGrade()).isEqualTo(4);
		assertThat(Theme.PARENTS.minHotelGrade()).isEqualTo(4);
		assertThat(Theme.GOLF.minHotelGrade()).isEqualTo(3);
		assertThat(Theme.TREKKING.minHotelGrade()).isEqualTo(3);
	}

}
