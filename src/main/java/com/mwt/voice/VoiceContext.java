package com.mwt.voice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.mwt.tour.OptionCategory;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourStatus;

/**
 * 음성 대화의 프롬프트에 넣을 정보: 신청할 수 있는 상품 목록(출발일이 오늘 뒤인 네 테마의 상품, 마감 포함)과 지금 고른 내용이다.
 * VoiceService는 OpenAI의 답을 이 상품 목록으로만 거른다. 가격은 넣지 않는다 — 가격은 화면이 가격 계산으로 받아 보여 준다.
 */
record VoiceContext(List<Tour> tours, VoiceDraft draft) {

	VoiceContext {
		tours = List.copyOf(tours);
	}

	Optional<Tour> findTour(Long tourId) {
		return tours.stream().filter(tour -> tour.tourId().equals(tourId)).findFirst();
	}

	/** 상품 하나. title은 상품 목록의 이름이고, levels는 고를 수 있는 투어 등급, options는 그 상품의 옵션이다. */
	record Tour(Long tourId, Theme theme, String title, LocalDate departureDate, int duration, int remainingSeats,
			TourStatus status, List<TourLevel> levels, List<Option> options) {

		Tour {
			levels = List.copyOf(levels);
			options = List.copyOf(options);
		}

		boolean hasOption(Long optionId) {
			return options.stream().anyMatch(option -> option.optionId().equals(optionId));
		}

	}

	record Option(Long optionId, OptionCategory category, String name) {
	}

}
