package com.mwt.tour;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mwt.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 여행 상품 흐름을 처리하는 tour 모듈의 공개 Service다(설계산출물 4.4). 다른 모듈은 여행 상품의 상태를 이 클래스로만 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class TourService {

	private final TourRepository tourRepository;
	private final Clock clock;

	/** 상품 목록(MWT-UC4). 출발일이 오늘보다 뒤인 그 테마의 상품을 마감 상품까지 출발일 순으로 돌려준다. */
	@Transactional(readOnly = true)
	public List<TourSummary> getTours(Theme theme) {
		return tourRepository.findUpcomingTours(theme, LocalDate.now(clock)).stream()
			.map(TourSummary::from)
			.toList();
	}

	/** 고를 수 있는 투어 등급과 등급별 1인 가격, 제공 내용(MWT-UC4). 등급은 클래식, 그랜드, 프리미엄 순이다. */
	@Transactional(readOnly = true)
	public TourLevelsResponse getLevels(Long tourId) {
		Tour tour = getTour(tourId);
		Theme theme = tour.getTheme();
		List<TourLevelsResponse.Level> levels = tour.getPrices().stream()
			.sorted(Comparator.comparing(TourPrice::getLevel))
			.map(price -> {
				TourLevel level = price.getLevel();
				return new TourLevelsResponse.Level(level, level.getDisplayName(), price.getPrice(), level.getHotelGrade(),
						level.getMeal(), level.isIncludesChampagne());
			})
			.toList();
		return new TourLevelsResponse(theme.getService(), theme.getSouvenir(), theme.getVehicle(), levels);
	}

	/** 옵션 목록(MWT-UC6). 옵션마다 받은 투어 등급을 기준으로 한 1인 요금 차이를 붙여 종류별로 나눠 돌려준다. */
	@Transactional(readOnly = true)
	public TourOptionsResponse getOptions(Long tourId, TourLevel level) {
		Tour tour = getTour(tourId);
		tour.checkLevel(level);
		Map<OptionCategory, List<TourOptionsResponse.Option>> byCategory = tour.getOptions().stream()
			.sorted(Comparator.comparing(TourOption::getId))
			.collect(Collectors.groupingBy(TourOption::getCategory, () -> new EnumMap<>(OptionCategory.class),
					Collectors.mapping(option -> new TourOptionsResponse.Option(option.getId(), option.getName(),
							tour.optionPriceDiff(level, option.getId())), Collectors.toList())));
		return new TourOptionsResponse(
				byCategory.getOrDefault(OptionCategory.HOTEL, List.of()),
				byCategory.getOrDefault(OptionCategory.TRANSPORT, List.of()),
				byCategory.getOrDefault(OptionCategory.MEAL, List.of()),
				byCategory.getOrDefault(OptionCategory.EXTRA, List.of()));
	}

	/**
	 * 상품 하나를 읽는다(booking 모듈이 신청할 때 쓴다). 없는 상품이면 404다.
	 * 등급별 가격과 옵션은 지연 로딩이라 부르는 쪽의 트랜잭션 안에서 읽는다.
	 */
	@Transactional(readOnly = true)
	public Tour getTour(Long tourId) {
		if (tourId == null) {
			throw new BusinessException("여행 상품을 골라 주세요.");
		}
		return tourRepository.findById(tourId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "여행 상품을 찾을 수 없습니다."));
	}

	/**
	 * 음성에서 뽑은 테마와 출발일이 같은 후보 상품(MWT-UC5, voice 모듈이 쓴다).
	 * 출발일이 오늘이거나 지났으면 빈 목록이다 — 지난 상품도 상태가 DB에 남아 있어서다. 테마나 출발일을 뽑지 못했을 때(null)도 빈 목록이다.
	 */
	@Transactional(readOnly = true)
	public List<TourSummary> findTours(Theme theme, LocalDate departureDate) {
		if (theme == null || departureDate == null || !departureDate.isAfter(LocalDate.now(clock))) {
			return List.of();
		}
		return tourRepository.findByThemeAndDepartureDate(theme, departureDate).stream()
			.map(TourSummary::from)
			.toList();
	}

	/** 여행 상품 기획(MWT-UC8). 상품을 만들고 등급별 가격과 옵션 목록을 넣어 저장한 뒤 상품 ID를 돌려준다. */
	@Transactional
	public Long registerTour(RegisterTourRequest request) {
		Tour tour = new Tour(request.theme(), request.departureDate(), request.duration(), request.capacity(),
				LocalDate.now(clock));
		tour.addPrices(request.prices().stream()
			.map(price -> new TourPrice(price.level(), price.price()))
			.toList());
		tour.addOptions(request.transports().stream()
			.map(transport -> TourOption.transport(transport.name(), transport.unitPrice()))
			.toList());
		return tourRepository.save(tour).getId();
	}

}
