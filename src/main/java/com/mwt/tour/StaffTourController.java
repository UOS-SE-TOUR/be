package com.mwt.tour;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 상품 등록(설계산출물 4.4)과 상품 현황·출발·완료 처리 요청을 받는다. 고객용 주소와 섞이지 않게 /api/v1/staff/ 아래에 둔다.
 */
@Tag(name = "여행 상품 관리(직원)")
@RestController
@RequestMapping("/api/v1/staff/tours")
@RequiredArgsConstructor
public class StaffTourController {

	private static final String STATUS_CONFLICT = "지금 상태에서는 할 수 없음(출발은 모집 중·확정·마감, 완료는 진행 중만)";

	private final TourService tourService;

	@Operation(summary = "여행 상품 등록 (직원)",
			description = "테마·출발일·기간·정원과 등급별 1인 가격, 교통 선택지를 받아 상품을 만든다. 호텔·식사·추가 옵션은 서버가 붙인다.")
	@ApiResponse(responseCode = "201", description = "등록한 상품의 ID")
	@ApiResponse(responseCode = "400", description = "입력 오류, 업무 규칙 위반(출발일이 오늘이거나 지남, 정원·기간 범위, 빠지거나 겹친 등급별 가격, 기본 교통이 없거나 둘 이상 등)")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterTourResponse registerTour(@Valid @RequestBody RegisterTourRequest request) {
		return new RegisterTourResponse(tourService.registerTour(request));
	}

	@Operation(summary = "상품 현황 (직원)", description = "모든 상태의 상품을 출발일 순으로 돌려준다.")
	@ApiResponse(responseCode = "200", description = "상품과 정원, 신청 인원, 상태 목록. 없으면 빈 목록")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@GetMapping
	public List<StaffTourSummary> getTours() {
		return tourService.getStaffTours();
	}

	@Operation(summary = "상품 출발 처리 (직원)",
			description = "출발일을 기다리지 않고 바로 출발 처리한다. 매일 0시 예약 작업이 출발일에 하는 것과 같게 모집 중(3명 미만)이면 취소, 확정·마감이면 진행 중이 된다. 창고 물품은 확정 때 이미 줄었으므로 여기서는 줄이지 않는다.")
	@ApiResponse(responseCode = "200", description = "바뀐 상품")
	@ApiResponse(responseCode = "400", description = "상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = STATUS_CONFLICT)
	@PostMapping("/{tourId}/depart")
	public StaffTourSummary departTour(@PathVariable Long tourId) {
		return tourService.departTour(tourId);
	}

	@Operation(summary = "상품 완료 처리 (직원)", description = "여행 종료일을 기다리지 않고 진행 중인 상품을 바로 완료 처리한다.")
	@ApiResponse(responseCode = "200", description = "바뀐 상품")
	@ApiResponse(responseCode = "400", description = "상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@ApiResponse(responseCode = "409", description = STATUS_CONFLICT)
	@PostMapping("/{tourId}/complete")
	public StaffTourSummary completeTour(@PathVariable Long tourId) {
		return tourService.completeTour(tourId);
	}

}
