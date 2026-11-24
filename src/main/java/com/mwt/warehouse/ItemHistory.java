package com.mwt.warehouse;

import java.time.Instant;

import com.mwt.tour.Theme;
import com.mwt.tour.Tour;
import com.mwt.tour.TourSummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 창고 물품 수량이 바뀐 기록 한 줄이다(V7). 물품을 지워도 남도록 물품 이름·테마와 상품 이름을 그때 값으로 적는다.
 * 상품 확정 때 줄임과 늦은 취소로 되돌림에만 상품이 붙는다.
 */
@Entity
@Table(name = "item_history", indexes = @Index(name = "idx_item_history_tour", columnList = "tour_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long itemId;

	@Column(nullable = false, length = Item.NAME_MAX_LENGTH)
	private String itemName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Theme theme;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ItemHistoryReason reason;

	private Long tourId;

	@Column(length = 100)
	private String tourTitle;

	@Column(nullable = false)
	private int changeAmount;

	@Column(nullable = false)
	private int quantityAfter;

	@Column(nullable = false)
	private Instant createdAt;

	/** item은 바뀐 뒤의 물품이다. tour는 확정 때 줄임·늦은 취소로 되돌림일 때만 준다. */
	ItemHistory(Item item, ItemHistoryReason reason, Tour tour, int changeAmount, Instant createdAt) {
		this.itemId = item.getId();
		this.itemName = item.getName();
		this.theme = item.getTheme();
		this.reason = reason;
		this.tourId = tour == null ? null : tour.getId();
		this.tourTitle = tour == null ? null : TourSummary.title(tour);
		this.changeAmount = changeAmount;
		this.quantityAfter = item.getQuantity();
		this.createdAt = createdAt;
	}

}
