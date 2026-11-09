package com.mwt.warehouse;

import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

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
 * 창고 물품 엔티티다(설계산출물 4.8). 물품은 이름으로 찾으므로 name은 유일하다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

	static final int NAME_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = NAME_MAX_LENGTH)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Theme theme;

	private int quantity;

	/** 새 물품. 이름은 {@link #normalizeName}으로 앞뒤 공백을 떼고 확인해 넣는다. */
	Item(String name, Theme theme, int quantity) {
		if (theme == null) {
			throw new BusinessException("테마를 골라 주세요.");
		}
		checkQuantity(quantity);
		this.name = normalizeName(name);
		this.theme = theme;
		this.quantity = quantity;
	}

	/** 들어온 수량만큼 창고 수량을 늘린다. */
	public void addQuantity(int quantity) {
		checkQuantity(quantity);
		if (quantity > Integer.MAX_VALUE - this.quantity) {
			throw new BusinessException("창고에 둘 수 있는 수량을 넘습니다.");
		}
		this.quantity += quantity;
	}

	/** 앞뒤 공백을 뗀 물품 이름. 같은 물품인지는 이 값으로 찾는다. */
	static String normalizeName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException("물품 이름을 입력해 주세요.");
		}
		if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX_LENGTH) {
			throw new BusinessException("물품 이름은 " + NAME_MAX_LENGTH + "자까지 쓸 수 있습니다.");
		}
		return trimmed;
	}

	private static void checkQuantity(int quantity) {
		if (quantity < 1) {
			throw new BusinessException("수량은 1개 이상이어야 합니다.");
		}
	}

}
