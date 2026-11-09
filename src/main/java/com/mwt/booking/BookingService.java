package com.mwt.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mwt.tour.Tour;
import com.mwt.tour.TourService;

import lombok.RequiredArgsConstructor;

/**
 * 가격 계산과 이전 여행 흐름을 처리하는 booking 모듈의 Service다(설계산출물 4.5).
 */
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final TourService tourService;
	private final PriceCalculator priceCalculator;
	private final RegularDiscountPolicy regularDiscountPolicy;
	private final Clock clock;

	/** 이전 여행 조회(MWT-UC3). 완료 상태인 상품의 신청을 출발일이 최근인 순서로 돌려준다. */
	@Transactional(readOnly = true)
	public List<PreviousTourResponse> getPreviousTours(Long memberId) {
		return bookingRepository.findPreviousBookings(memberId).stream()
			.map(PreviousTourResponse::from)
			.toList();
	}

	/**
	 * 가격 계산(MWT-UC4·UC5·UC6·UC7). 고른 내용을 확인하고 할인 전 총 가격을 계산한 뒤, 이전 여행 횟수로 단골 할인을 정한다.
	 * 가격은 저장하지 않고 부를 때마다 할인 전 총 가격에서 새로 계산한다.
	 */
	@Transactional(readOnly = true)
	public PriceQuote quote(Long memberId, QuoteRequest request) {
		Tour tour = tourService.getTour(request.tourId());
		List<Long> optionIds = request.optionIdsOrEmpty();
		tour.checkApplicable(request.level(), request.headcount(), optionIds, LocalDate.now(clock));
		long totalPrice = priceCalculator.calculate(tour, request.level(), request.headcount(), optionIds);
		long previousCount = bookingRepository.countPreviousBookings(memberId);
		long discount = regularDiscountPolicy.discount(previousCount, totalPrice);
		return PriceQuote.of(tour, request.level(), request.headcount(), optionIds, totalPrice, discount);
	}

}
