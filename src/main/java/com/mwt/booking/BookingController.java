package com.mwt.booking;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 고객의 이전 여행 조회, 가격 계산, 신청 요청을 받는다(설계산출물 4.5, 4.9 서버 API 표).
 * 로그인한 회원의 ID는 로그인 토큰을 확인한 common.security가 Spring Security에 넣어 둔 값이다.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;

	@GetMapping("/previous")
	public List<PreviousTourResponse> getPreviousTours(@AuthenticationPrincipal Long memberId) {
		return bookingService.getPreviousTours(memberId);
	}

	@PostMapping("/quote")
	public PriceQuote quote(@AuthenticationPrincipal Long memberId, @Valid @RequestBody QuoteRequest request) {
		return bookingService.quote(memberId, request);
	}

	/** 신청 내용은 가격 계산과 같다. 더는 신청할 수 없는 상품이면 409, 고른 내용이 맞지 않으면 400이다(Tour.checkApplicable). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApplyTourResponse applyTour(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody QuoteRequest request) {
		return bookingService.applyTour(memberId, request);
	}

}
