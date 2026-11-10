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

import com.mwt.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 고객의 이전 여행 조회, 가격 계산, 신청 요청을 받는다(설계산출물 4.5, 4.9 서버 API 표).
 * 로그인한 회원의 ID는 로그인 토큰을 확인한 common.security가 Spring Security에 넣어 둔 값이다.
 */
@Tag(name = "가격 계산·신청·이전 여행")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

	private static final String CANNOT_APPLY = "그 상품을 더는 신청할 수 없음(자리가 다 참, 출발일이 오늘이거나 지남, 취소·진행 중·완료된 상품). 화면은 상품 목록으로 돌아간다";

	private final BookingService bookingService;

	@Operation(summary = "이전 여행 목록 (고객)", description = "완료(COMPLETED)된 상품의 내 신청을 출발일이 최근인 순서로 돌려준다.")
	@ApiResponse(responseCode = "200", description = "이전 여행 목록. 없으면 빈 목록")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_CUSTOMER)
	@GetMapping("/previous")
	public List<PreviousTourResponse> getPreviousTours(@AuthenticationPrincipal Long memberId) {
		return bookingService.getPreviousTours(memberId);
	}

	@Operation(summary = "가격 계산 (고객)",
			description = "고른 상품·투어 등급·인원·옵션으로 할인 전 총 가격, 단골 할인, 최종 가격을 계산한다. 신청하지는 않고 저장하지도 않는다.")
	@ApiResponse(responseCode = "200", description = "고른 내용과 가격")
	@ApiResponse(responseCode = "400", description = "입력 오류, 고른 내용이 맞지 않음(남은 자리보다 많은 인원, 그 테마에서 고를 수 없는 등급·인원, 그 상품의 것이 아닌 옵션 등)")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_CUSTOMER)
	@ApiResponse(responseCode = "404", description = OpenApiConfig.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = CANNOT_APPLY)
	@PostMapping("/quote")
	public PriceQuote quote(@AuthenticationPrincipal Long memberId, @Valid @RequestBody QuoteRequest request) {
		return bookingService.quote(memberId, request);
	}

	/** 신청 내용은 가격 계산과 같다. 더는 신청할 수 없는 상품이면 409, 고른 내용이 맞지 않으면 400이다(Tour.checkApplicable). */
	@Operation(summary = "신청 (고객)",
			description = "가격 계산과 같은 내용을 보내 신청한다. 이 신청으로 상품이 확정(신청 인원 3명 이상)되면 신청자 모두에게, 이미 확정된 상품이면 이 신청자에게 확정 문자를 보낸다.")
	@ApiResponse(responseCode = "201", description = "신청 ID와 신청 뒤 상품 상태")
	@ApiResponse(responseCode = "400", description = "입력 오류, 고른 내용이 맞지 않음(가격 계산과 같다)")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_CUSTOMER)
	@ApiResponse(responseCode = "404", description = OpenApiConfig.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = CANNOT_APPLY)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApplyTourResponse applyTour(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody QuoteRequest request) {
		return bookingService.applyTour(memberId, request);
	}

}
