package com.mwt.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 단골 할인(설계산출물 4.5 RegularDiscountPolicy, 5.7). 이전 여행 2건 이상이면 10%, 최종 가격은 90%에서 원 미만을 버린다.
 */
class RegularDiscountPolicyTest {

	private final RegularDiscountPolicy policy = new RegularDiscountPolicy();

	@ParameterizedTest
	@ValueSource(longs = {0, 1})
	void 이전_여행이_2건_미만이면_할인하지_않는다(long previousCount) {
		assertThat(policy.discount(previousCount, 4_500_000)).isZero();
	}

	@ParameterizedTest
	@ValueSource(longs = {2, 5})
	void 이전_여행이_2건_이상이면_10퍼센트를_할인한다(long previousCount) {
		assertThat(policy.discount(previousCount, 4_500_000)).isEqualTo(450_000);
	}

	@Test
	void 최종_가격의_원_미만은_버리고_할인_금액은_총_가격에서_최종_가격을_뺀_값() {
		// 1,234,567원의 90%는 1,111,110.3원 → 최종 1,111,110원, 할인 123,457원
		assertThat(policy.discount(2, 1_234_567)).isEqualTo(123_457);
		// 15원의 90%는 13.5원 → 최종 13원, 할인 2원
		assertThat(policy.discount(2, 15)).isEqualTo(2);
	}

}
