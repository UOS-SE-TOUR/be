package com.mwt.booking;

import java.util.Collection;

import org.springframework.stereotype.Component;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourPrice;

/**
 * 할인 전 총 가격을 계산한다(설계산출물 4.5, 1.3 (6)). 1인 가격은 투어 등급 가격에 고른 옵션마다 Tour.optionPriceDiff를 더한 값이고,
 * 총 가격은 1인 가격 × 인원이다. 고른 내용이 그 상품에 맞는지는 먼저 Tour.checkApplicable로 확인한다.
 */
@Component
class PriceCalculator {

	long calculate(Tour tour, TourLevel level, int headcount, Collection<Long> optionIds) {
		long perPerson = levelPrice(tour, level);
		for (Long optionId : optionIds) {
			perPerson = Math.addExact(perPerson, tour.optionPriceDiff(level, optionId));
		}
		if (perPerson < 0) {
			// 낮은 호텔·식사를 고르면 차이만큼 빼는데, 등급 가격이 그 차이보다 작게 등록된 상품이다(등록은 1원 이상만 본다)
			throw new BusinessException("고른 옵션으로는 가격을 계산할 수 없습니다. 다른 옵션을 골라 주세요.");
		}
		return Math.multiplyExact(perPerson, headcount);
	}

	private static long levelPrice(Tour tour, TourLevel level) {
		return tour.getPrices().stream()
			.filter(price -> price.getLevel() == level)
			.findFirst()
			.map(TourPrice::getPrice)
			.orElseThrow(() -> new IllegalStateException("상품 " + tour.getId() + "에 " + level + " 가격이 없다."));
	}

}
