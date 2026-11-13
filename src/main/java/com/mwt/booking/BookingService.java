package com.mwt.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

import lombok.extern.slf4j.Slf4j;

/**
 * 가격 계산, 신청, 이전 여행 흐름을 처리하는 booking 모듈의 Service다(설계산출물 4.5).
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
	private final Clock clock;
	private final TransactionTemplate applyTransaction;
	private final TransactionTemplate readOnlyTransaction;

	public BookingService(BookingRepository bookingRepository, TourService tourService, MemberService memberService,
			SmsSender smsSender, PriceCalculator priceCalculator, RegularDiscountPolicy regularDiscountPolicy, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.bookingRepository = bookingRepository;
		this.tourService = tourService;
		this.memberService = memberService;
		this.smsSender = smsSender;
		this.priceCalculator = priceCalculator;
		this.regularDiscountPolicy = regularDiscountPolicy;
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
			log.info("같은 신청 키의 신청이 먼저 저장되어 이 신청을 되돌리고 그 결과를 돌려준다: 회원 {}, 신청 {}, 되돌린 까닭 {}", memberId,
					saved.get().bookingId(), e.toString());
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
	 * 상품이 확정 상태면 신청이 커밋된 뒤 확정 문자를 보낸다.
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
