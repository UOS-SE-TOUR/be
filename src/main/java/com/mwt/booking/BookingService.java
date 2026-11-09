package com.mwt.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mwt.member.Member;
import com.mwt.member.MemberService;
import com.mwt.notification.SmsSender;
import com.mwt.tour.ConfirmChange;
import com.mwt.tour.Tour;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourService;
import com.mwt.tour.TourSummary;

import lombok.RequiredArgsConstructor;

/**
 * 가격 계산, 신청, 이전 여행 흐름을 처리하는 booking 모듈의 Service다(설계산출물 4.5).
 */
@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final TourService tourService;
	private final MemberService memberService;
	private final SmsSender smsSender;
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
		Price price = price(memberId, tour, request);
		return PriceQuote.of(tour, request.level(), request.headcount(), request.optionIdsOrEmpty(), price.totalPrice(),
				price.discount());
	}

	/**
	 * 신청(MWT-UC4, 설계산출물 5.4·5.4.1). quote와 같은 확인과 계산을 한 번 더 하고, 신청 인원을 더한 뒤 신청을 저장한다.
	 * 신청 인원을 더하기 전에 저장하면 같은 상품의 동시 신청이 교착한다(TourService.addApplicants). 정원을 넘어 거부되면 저장하지 않는다.
	 * 상품이 확정 상태면 신청이 커밋된 뒤 확정 문자를 보낸다.
	 */
	@Transactional
	public ApplyTourResponse applyTour(Long memberId, QuoteRequest request) {
		Tour tour = tourService.getTour(request.tourId());
		Price price = price(memberId, tour, request);
		Member member = memberService.getMember(memberId);
		ConfirmChange change = tourService.addApplicants(tour.getId(), request.headcount());
		Booking booking = bookingRepository.save(new Booking(member, tour, request.level(), request.headcount(),
				chosenOptions(tour, request.optionIdsOrEmpty()), price.totalPrice(), price.discount(), clock.instant()));
		List<String> recipients = switch (change) {
			case NOT_CONFIRMED -> List.of();
			case NEWLY_CONFIRMED -> bookingRepository.findByTourId(tour.getId()).stream()
				.map(applied -> applied.getMember().getPhone())
				.toList();
			case ALREADY_CONFIRMED -> List.of(member.getPhone());
		};
		if (!recipients.isEmpty()) {
			sendAfterCommit(recipients, confirmMessage(tour));
		}
		return new ApplyTourResponse(booking.getId(), tour.getStatus());
	}

	/** 확정 문자. 상품 이름에 출발일이 들어 있다(예: "골프 챌린지 투어 12/1 출발 5일"). */
	static String confirmMessage(Tour tour) {
		return "[미스터 월드] 신청하신 여행 상품(" + TourSummary.title(tour) + ")이 확정되었습니다.";
	}

	/** 고른 내용을 확인하고 할인 전 총 가격과 단골 할인 금액을 계산한다. */
	private Price price(Long memberId, Tour tour, QuoteRequest request) {
		List<Long> optionIds = request.optionIdsOrEmpty();
		tour.checkApplicable(request.level(), request.headcount(), optionIds, LocalDate.now(clock));
		long totalPrice = priceCalculator.calculate(tour, request.level(), request.headcount(), optionIds);
		long previousCount = bookingRepository.countPreviousBookings(memberId);
		return new Price(totalPrice, regularDiscountPolicy.discount(previousCount, totalPrice));
	}

	private static List<TourOption> chosenOptions(Tour tour, List<Long> optionIds) {
		return tour.getOptions().stream()
			.filter(option -> optionIds.contains(option.getId()))
			.toList();
	}

	/**
	 * 신청이 커밋된 뒤에 문자를 보낸다. 되돌려지면 보내지 않는다. sendSms는 실패해도 던지지 않고 로그에 남긴다(SmsSender).
	 * 보내는 동안 신청 응답이 기다린다 — 솔라피가 느리면 그만큼 늦어진다(SmsSenderConfig의 시간 제한).
	 */
	private void sendAfterCommit(List<String> phoneNumbers, String message) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				smsSender.sendSms(phoneNumbers, message);
			}
		});
	}

	private record Price(long totalPrice, long discount) {
	}

}
