package com.mwt.tour;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 여행 상품 흐름을 처리하는 tour 모듈의 공개 Service다(설계산출물 4.4). 다른 모듈은 여행 상품의 상태를 이 클래스로만 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class TourService {

	private final TourRepository tourRepository;
	private final Clock clock;

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
