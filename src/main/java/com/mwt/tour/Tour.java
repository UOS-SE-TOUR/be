package com.mwt.tour;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 상품 엔티티다(설계산출물 4.4). 등급별 가격(TourPrice)과 옵션(TourOption)은 상품에 딸려 함께 저장되고 지워진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tour {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Theme theme;

	@Column(nullable = false)
	private LocalDate departureDate;

	private int duration;

	private int capacity;

	private int appliedCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TourStatus status;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "tour_id", nullable = false)
	private List<TourPrice> prices = new ArrayList<>();

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "tour_id", nullable = false)
	private List<TourOption> options = new ArrayList<>();

}
