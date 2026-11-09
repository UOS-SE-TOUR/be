package com.mwt.tour;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 고객의 상품 조회 요청(상품 목록, 투어 등급, 옵션 목록)을 받는다(설계산출물 4.4, 4.9 서버 API 표).
 */
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
public class TourController {

	private final TourService tourService;

	@GetMapping
	public List<TourSummary> getTours(@RequestParam Theme theme) {
		return tourService.getTours(theme);
	}

	@GetMapping("/{tourId}/levels")
	public TourLevelsResponse getLevels(@PathVariable Long tourId) {
		return tourService.getLevels(tourId);
	}

	@GetMapping("/{tourId}/options")
	public TourOptionsResponse getOptions(@PathVariable Long tourId, @RequestParam TourLevel level) {
		return tourService.getOptions(tourId, level);
	}

}
