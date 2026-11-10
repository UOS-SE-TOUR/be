package com.mwt.tour;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 고객의 상품 조회 요청(상품 목록, 투어 등급, 옵션 목록)을 받는다(설계산출물 4.4, 4.9 서버 API 표).
 */
@Tag(name = "여행 상품")
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
public class TourController {

	private final TourService tourService;

	@Operation(summary = "테마별 여행 상품 목록 (고객)",
			description = "출발일이 오늘보다 뒤인 그 테마의 상품을 출발일 순으로 돌려준다. 마감(FULL) 상품도 들어 있다.")
	@ApiResponse(responseCode = "200", description = "상품 목록. 없으면 빈 목록")
	@ApiResponse(responseCode = "400", description = "테마가 없거나 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_CUSTOMER)
	@GetMapping
	public List<TourSummary> getTours(@RequestParam Theme theme) {
		return tourService.getTours(theme);
	}

	@Operation(summary = "상품의 투어 등급과 등급별 1인 가격 (고객)",
			description = "그 상품에서 고를 수 있는 투어 등급을 클래식, 그랜드, 프리미엄 순으로 돌려준다. 허니문·효도 투어는 그랜드와 프리미엄만 있다.")
	@ApiResponse(responseCode = "200", description = "테마가 주는 서비스·기념품·차량과 등급 목록")
	@ApiResponse(responseCode = "400", description = "상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_CUSTOMER)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@GetMapping("/{tourId}/levels")
	public TourLevelsResponse getLevels(@PathVariable Long tourId) {
		return tourService.getLevels(tourId);
	}

	@Operation(summary = "상품의 옵션 목록 (고객)",
			description = "호텔·교통·식사·추가 옵션을 종류별로 돌려준다. 요금 차이(priceDiff)는 받은 투어 등급을 기준으로 한 1인 요금 차이다.")
	@ApiResponse(responseCode = "200", description = "종류별 옵션 목록")
	@ApiResponse(responseCode = "400", description = "투어 등급이 없거나 그 상품의 테마에서 고를 수 없음, 상품 ID 형식이 올바르지 않음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_CUSTOMER)
	@ApiResponse(responseCode = "404", description = ApiDescriptions.TOUR_NOT_FOUND)
	@GetMapping("/{tourId}/options")
	public TourOptionsResponse getOptions(@PathVariable Long tourId, @RequestParam TourLevel level) {
		return tourService.getOptions(tourId, level);
	}

}
