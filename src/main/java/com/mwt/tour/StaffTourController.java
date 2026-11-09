package com.mwt.tour;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 상품 등록 요청을 받는다(설계산출물 4.4). 고객용 주소와 섞이지 않게 /api/v1/staff/ 아래에 둔다.
 */
@RestController
@RequestMapping("/api/v1/staff/tours")
@RequiredArgsConstructor
public class StaffTourController {

	private final TourService tourService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterTourResponse registerTour(@Valid @RequestBody RegisterTourRequest request) {
		return new RegisterTourResponse(tourService.registerTour(request));
	}

}
