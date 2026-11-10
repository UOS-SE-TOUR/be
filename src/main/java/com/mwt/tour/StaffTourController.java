package com.mwt.tour;

import org.springframework.http.HttpStatus;
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
 * 직원의 상품 등록 요청을 받는다(설계산출물 4.4). 고객용 주소와 섞이지 않게 /api/v1/staff/ 아래에 둔다.
 */
@Tag(name = "여행 상품 등록(직원)")
@RestController
@RequestMapping("/api/v1/staff/tours")
@RequiredArgsConstructor
public class StaffTourController {

	private final TourService tourService;

	@Operation(summary = "여행 상품 등록 (직원)",
			description = "테마·출발일·기간·정원과 등급별 1인 가격, 교통 선택지를 받아 상품을 만든다. 호텔·식사·추가 옵션은 서버가 붙인다.")
	@ApiResponse(responseCode = "201", description = "등록한 상품의 ID")
	@ApiResponse(responseCode = "400", description = "입력 오류, 업무 규칙 위반(출발일이 오늘이거나 지남, 정원·기간 범위, 빠지거나 겹친 등급별 가격, 기본 교통이 없거나 둘 이상 등)")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_STAFF)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterTourResponse registerTour(@Valid @RequestBody RegisterTourRequest request) {
		return new RegisterTourResponse(tourService.registerTour(request));
	}

}
