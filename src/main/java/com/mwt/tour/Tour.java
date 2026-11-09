package com.mwt.tour;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mwt.common.exception.BusinessException;

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
 * 여행 상품 엔티티다(설계산출물 4.4). 등급별 가격(TourPrice)과 옵션(TourOption)은 상품에 딸려 함께 저장된다.
 * "오늘"은 Clock에서 얻어 인자(today)로 받는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tour {

	private static final int MIN_CAPACITY = 3;
	// MySQL DATE에 넣을 수 있는 마지막 날. 여행 종료일(출발일 + 기간 − 1일)까지 이 안에 있어야 저장하고 계산할 수 있다
	private static final LocalDate LAST_STORABLE_DATE = LocalDate.of(9999, 12, 31);

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

	public Tour(Theme theme, LocalDate departureDate, int duration, int capacity, LocalDate today) {
		if (theme == null) {
			throw new BusinessException("테마를 골라 주세요.");
		}
		if (departureDate == null) {
			throw new BusinessException("출발일을 입력해 주세요.");
		}
		if (!departureDate.isAfter(today)) {
			throw new BusinessException("출발일은 오늘보다 뒤여야 합니다.");
		}
		if (duration < 1) {
			throw new BusinessException("기간은 1일 이상이어야 합니다.");
		}
		if (departureDate.isAfter(LAST_STORABLE_DATE) || departureDate.plusDays(duration - 1L).isAfter(LAST_STORABLE_DATE)) {
			throw new BusinessException("여행은 9999년 12월 31일 안에 끝나야 합니다.");
		}
		if (capacity < MIN_CAPACITY) {
			throw new BusinessException("정원은 " + MIN_CAPACITY + "명 이상이어야 합니다.");
		}
		if (theme == Theme.HONEYMOON && capacity % Theme.HONEYMOON_HEADCOUNT != 0) {
			throw new BusinessException(theme.getDisplayName() + "의 정원은 짝수여야 합니다.");
		}
		this.theme = theme;
		this.departureDate = departureDate;
		this.duration = duration;
		this.capacity = capacity;
		this.appliedCount = 0;
		this.status = TourStatus.RECRUITING;
	}

	public List<TourPrice> getPrices() {
		return Collections.unmodifiableList(prices);
	}

	public List<TourOption> getOptions() {
		return Collections.unmodifiableList(options);
	}

	/** 테마가 허용하는 등급마다 가격이 하나씩 있어야 한다. 허니문·효도 투어에 클래식 가격이 들어오면 거부한다. */
	public void addPrices(List<TourPrice> newPrices) {
		if (!prices.isEmpty()) {
			throw new IllegalStateException("등급별 가격은 한 번만 넣는다. 상품 " + id);
		}
		if (newPrices == null || newPrices.isEmpty()) {
			throw new BusinessException("등급별 가격을 입력해 주세요.");
		}
		Set<TourLevel> levels = EnumSet.noneOf(TourLevel.class);
		for (TourPrice price : newPrices) {
			if (!theme.allows(price.getLevel())) {
				throw new BusinessException(theme.getDisplayName() + "는 그랜드와 프리미엄 가격만 입력합니다.");
			}
			if (!levels.add(price.getLevel())) {
				throw new BusinessException(price.getLevel().getDisplayName() + " 가격이 두 번 들어 있습니다.");
			}
		}
		for (TourLevel level : TourLevel.values()) {
			if (theme.allows(level) && !levels.contains(level)) {
				throw new BusinessException(level.getDisplayName() + " 가격을 입력해 주세요.");
			}
		}
		prices.addAll(newPrices);
	}

	/**
	 * 직원이 입력한 교통 선택지(TourOption.transport)에 호텔 등급·식사·추가 옵션을 붙여 옵션 목록을 만든다(설계산출물 1.3 (5)).
	 * 추가 요금이 0원인 기본 교통이 정확히 하나여야 한다.
	 */
	public void addOptions(List<TourOption> transports) {
		if (!options.isEmpty()) {
			throw new IllegalStateException("옵션은 한 번만 넣는다. 상품 " + id);
		}
		if (transports == null || transports.isEmpty()) {
			throw new BusinessException("교통 선택지를 입력해 주세요.");
		}
		Set<String> names = new HashSet<>();
		int baseTransports = 0;
		for (TourOption transport : transports) {
			if (transport.getCategory() != OptionCategory.TRANSPORT) {
				throw new IllegalArgumentException("교통 선택지만 받는다: " + transport.getCategory());
			}
			if (!names.add(transport.getName())) {
				throw new BusinessException("같은 이름의 교통 선택지가 두 개 있습니다: " + transport.getName());
			}
			if (transport.getUnitPrice() == 0) {
				baseTransports++;
			}
		}
		if (baseTransports == 0) {
			throw new BusinessException("추가 요금이 0원인 기본 교통을 넣어 주세요.");
		}
		if (baseTransports > 1) {
			throw new BusinessException("추가 요금이 0원인 기본 교통은 하나만 넣을 수 있습니다.");
		}
		options.addAll(transports);
		options.addAll(TourOption.standardOptions(theme));
	}

	/**
	 * 고른 투어 등급을 기준으로 한 옵션의 1인 요금 차이다(설계산출물 1.3 (6)).
	 * 호텔·식사는 그 등급의 기본 제공 옵션 단가와의 차이, 교통은 단가(기본 교통 대비 추가 요금) 그대로,
	 * 추가 옵션은 그 등급의 기본 제공이면 0이고 아니면 단가 그대로다.
	 */
	public long optionPriceDiff(TourLevel level, Long optionId) {
		checkLevel(level);
		TourOption option = findOption(optionId);
		return switch (option.getCategory()) {
			case HOTEL, MEAL -> option.getUnitPrice() - defaultOption(option.getCategory(), level).getUnitPrice();
			case TRANSPORT -> option.getUnitPrice();
			case EXTRA -> option.getDefaultFor() == level ? 0 : option.getUnitPrice();
		};
	}

	/**
	 * 신청할 수 있는지 확인한다: 출발일이 오늘보다 뒤인지, 모집 중·확정 상태인지, 인원이 1 이상이고 남은 자리 안인지,
	 * 테마 규칙에 맞는지, 고른 옵션이 이 상품의 것인지. 호텔·교통·식사는 종류마다 하나만 고를 수 있다.
	 */
	public void checkApplicable(TourLevel level, int headcount, Collection<Long> optionIds, LocalDate today) {
		if (!departureDate.isAfter(today)) {
			throw new BusinessException("출발일이 오늘이거나 지난 상품은 신청할 수 없습니다.");
		}
		if (status == TourStatus.FULL || remainingSeats() <= 0) {
			throw new BusinessException("마감된 상품입니다.");
		}
		if (status != TourStatus.RECRUITING && status != TourStatus.CONFIRMED) {
			throw new BusinessException("신청할 수 없는 상품입니다.");
		}
		if (headcount < 1) {
			throw new BusinessException("인원은 1명 이상이어야 합니다.");
		}
		if (headcount > remainingSeats()) {
			throw new BusinessException("남은 자리보다 인원이 많습니다.");
		}
		checkLevel(level);
		theme.checkHeadcount(headcount);
		checkOptions(optionIds);
	}

	public int remainingSeats() {
		return capacity - appliedCount;
	}

	private void checkLevel(TourLevel level) {
		if (level == null) {
			throw new BusinessException("투어 등급을 골라 주세요.");
		}
		if (!theme.allows(level)) {
			throw new BusinessException(theme.getDisplayName() + "는 그랜드와 프리미엄만 고를 수 있습니다.");
		}
	}

	private void checkOptions(Collection<Long> optionIds) {
		if (optionIds == null) {
			return;
		}
		Set<Long> chosen = new HashSet<>();
		Set<OptionCategory> chosenCategories = EnumSet.noneOf(OptionCategory.class);
		for (Long optionId : optionIds) {
			if (!chosen.add(optionId)) {
				throw new BusinessException("같은 옵션을 두 번 고를 수 없습니다.");
			}
			OptionCategory category = findOption(optionId).getCategory();
			if (category != OptionCategory.EXTRA && !chosenCategories.add(category)) {
				throw new BusinessException("호텔·교통·식사는 종류마다 하나만 고를 수 있습니다.");
			}
		}
	}

	private TourOption findOption(Long optionId) {
		return options.stream()
			.filter(option -> option.getId() != null && option.getId().equals(optionId))
			.findFirst()
			.orElseThrow(() -> new BusinessException("이 상품에 없는 옵션입니다."));
	}

	private TourOption defaultOption(OptionCategory category, TourLevel level) {
		return options.stream()
			.filter(option -> option.getCategory() == category && option.getDefaultFor() == level)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("상품 " + id + "에 " + level + "의 기본 " + category + " 옵션이 없다."));
	}

}
