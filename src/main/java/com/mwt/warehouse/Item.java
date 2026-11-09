package com.mwt.warehouse;

import com.mwt.common.exception.BusinessException;
import com.mwt.common.text.TextInput;
import com.mwt.tour.Theme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 창고 물품 엔티티다(설계산출물 4.8). 물품은 이름과 테마로 찾으므로 (이름, 테마)는 유일하다 — 같은 이름도 테마마다 따로 있다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_item_name_theme", columnNames = {"name", "theme"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

	static final int NAME_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Theme theme;

	private int quantity;

	Item(String name, Theme theme, int quantity) {
		checkTheme(theme);
		checkQuantity(quantity);
		this.name = normalizeName(name);
		this.theme = theme;
		this.quantity = quantity;
	}

	public void addQuantity(int quantity) {
		checkQuantity(quantity);
		if (quantity > Integer.MAX_VALUE - this.quantity) {
			throw new BusinessException("창고에 둘 수 있는 수량을 넘습니다.");
		}
		this.quantity += quantity;
	}

	/** 같은 물품인지는 이 값과 테마로 찾는다. 자모를 풀어 쓴 한글과 보이지 않는 앞뒤 공백도 다듬어 같은 이름이 되게 한다(TextInput). */
	static String normalizeName(String name) {
		return TextInput.require(name, "물품 이름", NAME_MAX_LENGTH);
	}

	static void checkTheme(Theme theme) {
		if (theme == null) {
			throw new BusinessException("테마를 골라 주세요.");
		}
	}

	private static void checkQuantity(int quantity) {
		if (quantity < 1) {
			throw new BusinessException("수량은 1개 이상이어야 합니다.");
		}
	}

}
