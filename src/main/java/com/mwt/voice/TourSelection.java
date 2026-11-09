package com.mwt.voice;

import java.time.LocalDate;
import java.util.List;

import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourSummary;

/**
 * 음성에서 뽑은 테마, 출발일, 투어 등급, 인원과, 테마와 출발일이 맞는 후보 상품 목록이다(설계산출물 v0.5 4.7).
 * 뽑지 못한 항목은 null이고 후보가 없으면 빈 목록이다. 화면은 이 내용을 보여 주고, 고객이 고친 뒤 가격 계산(quote)으로 넘긴다.
 */
public record TourSelection(Theme theme, LocalDate departureDate, TourLevel level, Integer headcount,
		List<TourSummary> candidates) {

	public TourSelection {
		candidates = candidates == null ? List.of() : List.copyOf(candidates);
	}

	/** LlmClient가 뽑은 네 항목만 담는다. 후보 상품은 VoiceService가 채운다. */
	static TourSelection extracted(Theme theme, LocalDate departureDate, TourLevel level, Integer headcount) {
		return new TourSelection(theme, departureDate, level, headcount, List.of());
	}

	TourSelection withCandidates(List<TourSummary> candidates) {
		return new TourSelection(theme, departureDate, level, headcount, candidates);
	}

}
