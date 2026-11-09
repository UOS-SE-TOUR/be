package com.mwt.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * 단골 할인(설계산출물 4.5, MWT-UC7). 이전 여행이 2건 이상이면 10%를 할인한다(가정).
 * 최종 가격은 할인 전 총 가격의 90%에서 원 미만을 버린 값이고, 할인 금액은 할인 전 총 가격에서 최종 가격을 뺀 값이다.
 * 늘 할인 전 총 가격을 받아 새로 계산하므로 할인된 가격에 다시 할인하지 않는다.
 */
@Component
class RegularDiscountPolicy {

	static final BigDecimal RATE = new BigDecimal("0.1");
	static final long MIN_PREVIOUS = 2;

	/** 할인 금액을 돌려준다. 단골이 아니면 0이다. */
	long discount(long previousCount, long totalPrice) {
		if (previousCount < MIN_PREVIOUS) {
			return 0;
		}
		long finalPrice = BigDecimal.valueOf(totalPrice)
			.multiply(BigDecimal.ONE.subtract(RATE))
			.setScale(0, RoundingMode.DOWN)
			.longValueExact();
		return totalPrice - finalPrice;
	}

}
