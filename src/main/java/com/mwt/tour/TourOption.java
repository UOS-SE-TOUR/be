package com.mwt.tour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
 * 바꿀 수 있는 옵션과 그 1인 단가다(설계산출물 4.4). Tour에 딸린다.
 * 교통의 unitPrice는 기본 교통 대비 추가 요금이다. defaultFor는 이 옵션을 기본으로 주는 투어 등급이고, 기본 제공이 아니면 null이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourOption {

	static final int NAME_MAX_LENGTH = 100;

	// 상품을 등록할 때 교통 선택지에 붙이는 옵션의 1인 단가(설계산출물 1.3 옵션 기본 단가, 가정).
	// 호텔과 식사는 그 옵션을 기본으로 주는 투어 등급으로 찾는다(예: CLASSIC은 3등급 호텔, 도시락).
	private static final Map<TourLevel, Long> HOTEL_UNIT_PRICES = Map.of(
			TourLevel.CLASSIC, 100_000L, TourLevel.GRAND, 150_000L, TourLevel.PREMIUM, 250_000L);
	private static final Map<TourLevel, Long> MEAL_UNIT_PRICES = Map.of(
			TourLevel.CLASSIC, 10_000L, TourLevel.GRAND, 30_000L, TourLevel.PREMIUM, 80_000L);
	private static final long CHAMPAGNE_UNIT_PRICE = 50_000L;
	private static final long COFFEE_UNIT_PRICE = 5_000L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OptionCategory category;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	private long unitPrice;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private TourLevel defaultFor;

	private TourOption(OptionCategory category, String name, long unitPrice, TourLevel defaultFor) {
		this.category = category;
		this.name = name;
		this.unitPrice = unitPrice;
		this.defaultFor = defaultFor;
	}

	/** 직원이 입력한 교통 선택지 하나. unitPrice는 기본 교통 대비 1인 추가 요금이고, 기본 교통은 0원이다. */
	public static TourOption transport(String name, long unitPrice) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException("교통 이름을 입력해 주세요.");
		}
		if (trimmed.length() > NAME_MAX_LENGTH) {
			throw new BusinessException("교통 이름은 " + NAME_MAX_LENGTH + "자까지 쓸 수 있습니다.");
		}
		if (unitPrice < 0) {
			throw new BusinessException("교통 추가 요금은 0원 이상이어야 합니다.");
		}
		return new TourOption(OptionCategory.TRANSPORT, trimmed, unitPrice, null);
	}

	/**
	 * 교통 선택지에 붙이는 호텔 등급·식사·추가 옵션이다. defaultFor는 TourLevel의 기본 제공(hotelGrade, meal, includesChampagne)으로 채운다.
	 * 테마의 최소 호텔 등급보다 낮은 호텔은 넣지 않는다(허니문·효도 투어에는 3등급 호텔이 없다).
	 */
	static List<TourOption> standardOptions(Theme theme) {
		List<TourOption> standard = new ArrayList<>();
		for (TourLevel level : TourLevel.values()) {
			if (level.getHotelGrade() >= theme.minHotelGrade()) {
				standard.add(new TourOption(OptionCategory.HOTEL, level.getHotelGrade() + "등급 호텔",
						HOTEL_UNIT_PRICES.get(level), level));
			}
		}
		for (TourLevel level : TourLevel.values()) {
			standard.add(new TourOption(OptionCategory.MEAL, level.getMeal(), MEAL_UNIT_PRICES.get(level), level));
		}
		TourLevel champagneLevel = Arrays.stream(TourLevel.values())
			.filter(TourLevel::isIncludesChampagne)
			.findFirst()
			.orElse(null);
		standard.add(new TourOption(OptionCategory.EXTRA, "샴페인", CHAMPAGNE_UNIT_PRICE, champagneLevel));
		standard.add(new TourOption(OptionCategory.EXTRA, "커피", COFFEE_UNIT_PRICE, null));
		return standard;
	}

}
