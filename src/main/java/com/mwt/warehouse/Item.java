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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 창고 물품 엔티티다(설계산출물 4.8). 물품은 이름과 테마로 찾으므로 (이름, 테마)는 유일하다 — 같은 이름도 테마마다 따로 있다.
 * 1인당 개수는 상품이 확정될 때 고객 한 명에게 주는 개수다. 0이면 고객에게 주지 않는 물품이다.
 * 수량은 확정 때 모자라도 줄이므로 음수(모자란 만큼)일 수 있다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_item_name_theme", columnNames = {"name", "theme"}),
		indexes = @Index(name = "idx_item_theme", columnList = "theme"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

	static final int NAME_MAX_LENGTH = 100;
	static final int PER_PERSON_MAX = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Theme theme;

	private int perPerson;

	private int quantity;

	Item(String name, Theme theme, int perPerson, int quantity) {
		checkTheme(theme);
		checkPerPerson(perPerson);
		checkQuantity(quantity);
		this.name = normalizeName(name);
		this.theme = theme;
		this.perPerson = perPerson;
		this.quantity = quantity;
	}

	public void addQuantity(int quantity) {
		checkQuantity(quantity);
		long added = (long) this.quantity + quantity;
		if (added > Integer.MAX_VALUE) {
			throw new BusinessException("창고에 둘 수 있는 수량을 넘습니다.");
		}
		this.quantity = (int) added;
	}

	public void changePerPerson(int perPerson) {
		checkPerPerson(perPerson);
		this.perPerson = perPerson;
	}

	/** 상품이 확정되어 고객에게 줄 만큼(인원 × 1인당 개수) 줄인다. 모자라도 줄여 음수가 된다. */
	public void takeOut(int headcount) {
		if (headcount < 1) {
			throw new IllegalArgumentException("인원은 1명 이상이어야 한다: " + headcount);
		}
		long left = (long) this.quantity - (long) headcount * perPerson;
		if (left < Integer.MIN_VALUE) {
			throw new BusinessException("창고 물품 수량을 줄일 수 없어 신청할 수 없습니다.");
		}
		this.quantity = (int) left;
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

	private static void checkPerPerson(int perPerson) {
		if (perPerson < 0 || perPerson > PER_PERSON_MAX) {
			throw new BusinessException("1인당 개수는 0개부터 " + PER_PERSON_MAX + "개까지입니다.");
		}
	}

	private static void checkQuantity(int quantity) {
		if (quantity < 1) {
			throw new BusinessException("수량은 1개 이상이어야 합니다.");
		}
	}

}
