package com.mwt.tour;

import com.mwt.common.exception.BusinessException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투어 등급별 1인 가격이다(설계산출물 4.4). Tour에 딸리며, 한 상품에 등급마다 하나다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TourLevel level;

	private long price;

	public TourPrice(TourLevel level, long price) {
		if (level == null) {
			throw new BusinessException("투어 등급을 골라 주세요.");
		}
		if (price < 1) {
			throw new BusinessException(level.getDisplayName() + " 가격은 1원 이상이어야 합니다.");
		}
		this.level = level;
		this.price = price;
	}

}
