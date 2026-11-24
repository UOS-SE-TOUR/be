package com.mwt.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mwt.common.exception.BusinessException;
import com.mwt.member.Member;
import com.mwt.member.MemberService;
import com.mwt.notification.SmsSender;
import com.mwt.tour.ConfirmChange;
import com.mwt.tour.Tour;
import com.mwt.tour.TourOption;
import com.mwt.tour.TourService;
import com.mwt.tour.TourSummary;
import com.mwt.warehouse.WarehouseService;

import lombok.extern.slf4j.Slf4j;

/**
 * 가격 계산, 신청, 이전 여행, 내 신청 목록, 신청 내용 바꾸기 흐름을 처리하는 booking 모듈의 Service다(설계산출물 4.5).
 */
@Slf4j
@Service
public class BookingService {

	private static final String IDEMPOTENCY_KEY_CONFLICT = "같은 신청 키로 다른 내용을 신청할 수 없습니다.";

	private final BookingRepository bookingRepository;
	private final TourService tourService;
	private final MemberService memberService;
	private final SmsSender smsSender;
	private final PriceCalculator priceCalculator;
	private final RegularDiscountPolicy regularDiscountPolicy;
	private final WarehouseService warehouseService;
	private final Clock clock;
	private final TransactionTemplate applyTransaction;
	private final TransactionTemplate readOnlyTransaction;

	public BookingService(BookingRepository bookingRepository, TourService tourService, MemberService memberService,
			SmsSender smsSender, PriceCalculator priceCalculator, RegularDiscountPolicy regularDiscountPolicy,
			WarehouseService warehouseService, Clock clock, PlatformTransactionManager transactionManager) {
		this.bookingRepository = bookingRepository;
		this.tourService = tourService;
		this.memberService = memberService;
		this.smsSender = smsSender;
		this.priceCalculator = priceCalculator;
		this.regularDiscountPolicy = regularDiscountPolicy;
		this.warehouseService = warehouseService;
		this.clock = clock;
		this.applyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
	}

	/** 이전 여행 조회(MWT-UC3). 완료 상태인 상품의 신청을 출발일이 최근인 순서로 돌려준다. */
	@Transactional(readOnly = true)
	public List<PreviousTourResponse> getPreviousTours(Long memberId) {
		return bookingRepository.findPreviousBookings(memberId).stream()
			.map(PreviousTourResponse::from)
			.toList();
	}

	/**
	 * 신청 내역 조회(MWT-UC11). 상품 상태와 상관없이 그 회원의 신청 모두를 신청한 때가 최근인 순서로 돌려준다.
	 * 신청마다 오늘 바꿀 수 있는지(Tour.isChangeable)도 담는다.
	 */
	@Transactional(readOnly = true)
	public List<MyBookingResponse> getMyBookings(Long memberId) {
		LocalDate today = LocalDate.now(clock);
		return bookingRepository.findMyBookings(memberId).stream()
			.map(booking -> MyBookingResponse.from(booking, today))
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
	 * 신청(MWT-UC4, 설계산출물 4.5·5.4·5.4.1). 신청 키(idempotencyKey)가 있으면 상품을 확인하기 전에 같은 회원이 그 신청 키로 한
	 * 신청을 찾는다. 있으면 새로 신청하지 않고 그 신청의 ID와 상품의 지금 상태를 돌려주고, 내용(상품, 투어 등급, 인원, 옵션)이 다르면
	 * 400이다. 없거나 신청 키가 없으면 신청하고, 신청 키를 신청과 함께 저장한다.
	 *
	 * <p>같은 신청 키로 동시에 온 다른 요청이 먼저 커밋되면, 이 신청은 (회원, 신청 키) 유일 제약에 걸리거나 그 신청이 채운 자리 때문에
	 * 거부되어 통째로 되돌려진다(더한 인원도, 커밋 뒤 문자도 없다). 그래서 신청 키가 있는 신청이 어떤 이유로든 실패하면 새 읽기 전용
	 * 트랜잭션에서 그 신청 키의 신청을 다시 찾고, 없으면 원래 오류를 던진다. 되돌린 트랜잭션 안에서는 MySQL(REPEATABLE READ)이 처음
	 * 읽은 시점의 값을 줘서 먼저 저장된 신청이 보이지 않기 때문이다. 그러려면 신청하는 부분만 트랜잭션으로 묶어야 하므로, 신청 키가
	 * 있으면 트랜잭션 밖(Controller)에서만 부른다.
	 */
	public ApplyTourResponse applyTour(Long memberId, QuoteRequest request, String idempotencyKey) {
		if (idempotencyKey == null) {
			return applyTransaction.execute(status -> apply(memberId, request, null));
		}
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("신청 키가 있는 applyTour는 트랜잭션 밖에서 불러야 한다(실패하면 새 트랜잭션에서 다시 찾는다)");
		}
		Optional<ApplyTourResponse> earlier = earlierResult(memberId, request, idempotencyKey);
		if (earlier.isPresent()) {
			return earlier.get();
		}
		try {
			return applyTransaction.execute(status -> apply(memberId, request, idempotencyKey));
		} catch (RuntimeException e) {
			Optional<ApplyTourResponse> saved;
			try {
				saved = earlierResult(memberId, request, idempotencyKey);
			} catch (RuntimeException lookupFailure) {
				lookupFailure.addSuppressed(e);
				throw lookupFailure;
			}
			if (saved.isEmpty()) {
				throw e;
			}
			log.info("신청 키가 있는 신청이 실패했지만 그 신청 키의 신청이 저장되어 있어 그 결과를 돌려준다: 회원 {}, 신청 {}, 실패 {}",
					memberId, saved.get().bookingId(), e.toString());
			return saved.get();
		}
	}

	/**
	 * 같은 회원이 그 신청 키로 한 신청의 결과(신청 ID, 상품의 지금 상태). 내용이 다르면 400이다. 새 읽기 전용 트랜잭션에서 찾는다.
	 * 상태는 신청할 때가 아니라 지금 상태라, 그사이 출발 처리되었으면 취소·진행 중·완료일 수도 있다.
	 */
	private Optional<ApplyTourResponse> earlierResult(Long memberId, QuoteRequest request, String idempotencyKey) {
		return readOnlyTransaction.execute(status -> bookingRepository
			.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)
			.map(booking -> {
				if (!sameChoice(booking, request)) {
					throw new BusinessException(IDEMPOTENCY_KEY_CONFLICT);
				}
				return new ApplyTourResponse(booking.getId(), booking.getTour().getStatus());
			}));
	}

	/**
	 * 신청과 요청의 내용(상품, 투어 등급, 인원, 옵션)이 같은지 본다. 옵션은 순서와 상관없이 보되, 같은 옵션을 두 번 보낸 요청은
	 * 다른 내용으로 본다(처음 신청이었으면 Tour.checkApplicable이 거부했을 요청이다).
	 */
	private static boolean sameChoice(Booking booking, QuoteRequest request) {
		Set<Long> savedOptionIds = booking.getOptions().stream()
			.map(TourOption::getId)
			.collect(Collectors.toSet());
		List<Long> requestedOptionIds = request.optionIdsOrEmpty();
		return booking.getTour().getId().equals(request.tourId()) && booking.getLevel() == request.level()
				&& Objects.equals(booking.getHeadcount(), request.headcount())
				&& requestedOptionIds.size() == savedOptionIds.size()
				&& savedOptionIds.equals(new HashSet<>(requestedOptionIds));
	}

	/**
	 * 신청하는 부분(applyTour가 트랜잭션으로 묶는다). quote와 같은 확인과 계산을 한 번 더 하고, 신청 인원을 더한 뒤 신청을 저장한다.
	 * 신청 인원을 더하기 전에 저장하면 같은 상품의 동시 신청이 교착한다(TourService.addApplicants). 정원을 넘어 거부되면 저장하지 않는다.
	 * 상품이 확정 상태면 같은 트랜잭션에서 그 테마 창고 물품을 줄이고, 신청이 커밋된 뒤 확정 문자를 보낸다.
	 */
	private ApplyTourResponse apply(Long memberId, QuoteRequest request, String idempotencyKey) {
		Tour tour = tourService.getTour(request.tourId());
		Price price = price(memberId, tour, request);
		Member member = memberService.getMember(memberId);
		ConfirmChange change = tourService.addApplicants(tour.getId(), request.headcount());
		Booking booking = bookingRepository.save(new Booking(member, tour, request.level(), request.headcount(),
				chosenOptions(tour, request.optionIdsOrEmpty()), price.totalPrice(), price.discount(), clock.instant(),
				idempotencyKey));
		List<String> recipients = switch (change) {
			case NOT_CONFIRMED -> List.of();
			case NEWLY_CONFIRMED -> bookingRepository.findByTourId(tour.getId()).stream()
				.map(applied -> applied.getMember().getPhone())
				.toList();
			case ALREADY_CONFIRMED -> List.of(member.getPhone());
		};
		takeOutItems(tour, change, request.headcount());
		if (!recipients.isEmpty()) {
			sendAfterCommit(recipients, confirmMessage(tour));
		}
		return new ApplyTourResponse(booking.getId(), tour.getStatus());
	}

	/**
	 * 상품이 확정되면 그 테마 창고 물품을 줄인다. 이번 신청으로 처음 확정됐으면 신청 인원 합계(이번 신청 포함)만큼, 이미 확정된
	 * 상품이면 이번 인원만큼이다. tour는 addApplicants가 잠그고 다시 읽어 인원을 더한 객체라 신청 인원 합계가 최신이다.
	 * 상품 행을 잠근 뒤에 창고 물품 행을 잠그고, 창고 쪽 요청은 상품 행을 잠그지 않으므로 서로 기다리다 교착하지 않는다.
	 */
	private void takeOutItems(Tour tour, ConfirmChange change, int headcount) {
		int takeOutHeadcount = switch (change) {
			case NOT_CONFIRMED -> 0;
			case NEWLY_CONFIRMED -> tour.getAppliedCount();
			case ALREADY_CONFIRMED -> headcount;
		};
		if (takeOutHeadcount > 0) {
			warehouseService.takeOutItems(tour, takeOutHeadcount);
		}
	}

	/**
	 * 신청 바꾸기 가격 계산(MWT-UC12). 그 회원의 신청을 바꿀 내용(투어 등급과 옵션)으로 가격을 계산해 돌려준다. 저장하지 않는다.
	 * 확인과 계산은 changeBooking과 같다.
	 */
	@Transactional(readOnly = true)
	public PriceQuote quoteChange(Long memberId, Long bookingId, ChangeBookingRequest request) {
		Booking booking = bookingRepository.findByIdAndMemberId(bookingId, memberId).orElseThrow(BookingService::notFound);
		Price price = changedPrice(memberId, booking, request, LocalDate.now(clock));
		return PriceQuote.of(booking.getTour(), request.level(), booking.getHeadcount(), request.optionIdsOrEmpty(),
				price.totalPrice(), price.discount());
	}

	/**
	 * 신청 내용 변경(MWT-UC12). 출발 전(Tour.checkChangeable)이면 그 회원의 신청의 투어 등급과 옵션을 바꾸고, 바꾸는 때를 기준으로 가격을
	 * 다시 정한다 — 할인 전 총 가격은 새 등급·옵션과 신청 인원으로, 단골 할인은 지금의 이전 여행 수로 정한다. 인원은 그대로라 상품의 신청
	 * 인원 합계와 상태, 확정 문자, 창고 물품 수량은 바꾸지 않는다. 바뀐 신청을 내 신청 목록의 한 줄로 돌려준다.
	 * <p>신청 키도 그대로라서, 바꾼 뒤 같은 신청 키로 처음 신청 내용을 다시 보내면 내용이 달라 400이다(알려진 한계, applyTour).
	 */
	@Transactional
	public MyBookingResponse changeBooking(Long memberId, Long bookingId, ChangeBookingRequest request) {
		Booking booking = bookingRepository.findByIdAndMemberIdForUpdate(bookingId, memberId)
			.orElseThrow(BookingService::notFound);
		LocalDate today = LocalDate.now(clock);
		Price price = changedPrice(memberId, booking, request, today);
		booking.change(request.level(), chosenOptions(booking.getTour(), request.optionIdsOrEmpty()), price.totalPrice(),
				price.discount());
		return MyBookingResponse.from(booking, today);
	}

	/**
	 * 바꿀 내용을 확인하고 할인 전 총 가격과 단골 할인 금액을 계산한다. 신청할 때의 price와 달리 남은 자리를 보지 않으므로
	 * Tour.checkChangeable로 확인하고, 인원은 신청 인원 그대로 쓴다.
	 */
	private Price changedPrice(Long memberId, Booking booking, ChangeBookingRequest request, LocalDate today) {
		Tour tour = booking.getTour();
		List<Long> optionIds = request.optionIdsOrEmpty();
		tour.checkChangeable(request.level(), optionIds, today);
		long totalPrice = priceCalculator.calculate(tour, request.level(), booking.getHeadcount(), optionIds);
		return withDiscount(memberId, totalPrice);
	}

	private static BusinessException notFound() {
		return new BusinessException(HttpStatus.NOT_FOUND, "신청을 찾을 수 없습니다.");
	}

	/** 확정 문자. 상품 이름에 출발일이 들어 있다(예: "골프 챌린지 투어 12/1 출발 5일"). */
	/**
	 * 직원 확정. 신청이 있는 모집 중 상품이면 3명이 안 돼도 확정하고, 신청이 3명을 채워 확정될 때와 같게 신청 인원 합계만큼 그 테마 창고 물품을
	 * 줄이고 커밋된 뒤 신청자 모두에게 확정 문자를 보낸다. 신청과 같게 상품 행을 먼저 잠그고 창고 물품을 잠근다.
	 */
	@Transactional
	public Tour confirmTour(Long tourId) {
		Tour tour = tourService.confirmTour(tourId);
		warehouseService.takeOutItems(tour, tour.getAppliedCount());
		List<String> recipients = bookingRepository.findByTourId(tour.getId()).stream()
			.map(applied -> applied.getMember().getPhone())
			.toList();
		if (!recipients.isEmpty()) {
			sendAfterCommit(recipients, confirmMessage(tour));
		}
		return tour;
	}

	/**
	 * 출발했지만 여행이 진행되지 않은 상품의 늦은 취소. 진행 중·완료 상품을 취소하고, 확정 때 그 상품 때문에 줄였던 창고 물품을 되돌린다.
	 * 예약 작업이 출발일에 취소할 때처럼 문자는 보내지 않는다.
	 */
	@Transactional
	public Tour cancelTour(Long tourId) {
		Tour tour = tourService.cancelTour(tourId);
		warehouseService.putBackItems(tour);
		return tour;
	}

	static String confirmMessage(Tour tour) {
		return "[미스터 월드] 신청하신 여행 상품(" + TourSummary.title(tour) + ")이 확정되었습니다.";
	}

	/** 고른 내용을 확인하고 할인 전 총 가격과 단골 할인 금액을 계산한다. */
	private Price price(Long memberId, Tour tour, QuoteRequest request) {
		List<Long> optionIds = request.optionIdsOrEmpty();
		tour.checkApplicable(request.level(), request.headcount(), optionIds, LocalDate.now(clock));
		long totalPrice = priceCalculator.calculate(tour, request.level(), request.headcount(), optionIds);
		return withDiscount(memberId, totalPrice);
	}

	/** 지금의 이전 여행 수로 단골 할인 금액을 정한다. */
	private Price withDiscount(Long memberId, long totalPrice) {
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
