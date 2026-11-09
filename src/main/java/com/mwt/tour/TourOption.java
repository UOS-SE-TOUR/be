package com.mwt.tour;

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
 * 바꿀 수 있는 옵션과 그 1인 단가다(설계산출물 4.4). Tour에 딸린다.
 * 교통의 unitPrice는 기본 교통 대비 추가 요금이다. defaultFor는 이 옵션을 기본으로 주는 투어 등급이고, 기본 제공이 아니면 null이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OptionCategory category;

	@Column(nullable = false, length = 100)
	private String name;

	private long unitPrice;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private TourLevel defaultFor;

}
