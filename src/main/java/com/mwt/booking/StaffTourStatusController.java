package com.mwt.booking;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.docs.ApiDescriptions;
import com.mwt.tour.StaffTourSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 상품 확정·늦은 취소 요청을 받는다. 상품 상태와 함께 창고 물품·확정 문자를 다루므로 tour가 아니라 booking 모듈에 둔다.
 */
@Tag(name = "여행 상품 관리(직원)")
@RestController
@RequestMapping("/api/v1/staff/tours")
@RequiredArgsConstructor
public class StaffTourStatusController {

	private final BookingService bookingService;

	@Operation(summary = "상품 확정 (직원)",
			description = "신청이 있는 모집 중 상품을 3명이 안 돼도 확정한다. 신청이 3명을 채워 확정될 때와 같게 신청 인원 합계만큼 그 테마 창고 물품을 줄이고, "
					+ "신청자 모두에게 확정 문자를 보낸다.")
	@ApiResponse(responseCode = "200", description = "확정한 상품")
	@ApiResponse(responseCode = "400", description = "상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = "모집 중이 아니거나 신청이 없는 상품")
	@PostMapping("/{tourId}/confirm")
	public StaffTourSummary confirmTour(@PathVariable Long tourId) {
		return StaffTourSummary.from(bookingService.confirmTour(tourId));
	}

	@Operation(summary = "상품 늦은 취소 (직원)",
			description = "출발했지만 여행이 진행되지 않은 진행 중·완료 상품을 취소하고, 확정 때 그 상품 때문에 줄였던 창고 물품을 되돌린다. 문자는 보내지 않는다.")
	@ApiResponse(responseCode = "200", description = "취소한 상품")
	@ApiResponse(responseCode = "400", description = "상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = "진행 중·완료가 아닌 상품")
	@PostMapping("/{tourId}/cancel")
	public StaffTourSummary cancelTour(@PathVariable Long tourId) {
		return StaffTourSummary.from(bookingService.cancelTour(tourId));
	}

}
