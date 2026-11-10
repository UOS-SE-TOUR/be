package com.mwt.voice;

import java.time.LocalDate;
import java.util.List;

import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성에서 뽑은 테마, 출발일, 투어 등급, 인원과, 테마와 출발일이 맞는 후보 상품 목록이다(설계산출물 v0.5 4.7).
 * 뽑지 못한 항목은 null이고 후보가 없으면 빈 목록이다. 화면은 이 내용을 보여 주고, 고객이 고친 뒤 가격 계산(quote)으로 넘긴다.
 */
@Schema(description = "음성에서 뽑은 조건과 후보 상품. 화면은 조건 칸을 채우고 후보 상품을 보여 준다")
public record TourSelection(@Schema(description = "말에서 뽑은 테마. 알아듣지 못하면 null", nullable = true) Theme theme,
		@Schema(description = "말에서 뽑은 출발일. 알아듣지 못하면 null", nullable = true) LocalDate departureDate,
		@Schema(description = "말에서 뽑은 투어 등급. 알아듣지 못하면 null", nullable = true) TourLevel level,
		@Schema(description = "말에서 뽑은 인원(명). 알아듣지 못하면 null", nullable = true) Integer headcount,
		@Schema(description = "테마와 출발일이 맞는 후보 상품. 테마나 출발일을 알아듣지 못했거나 출발일이 오늘이거나 지났으면 빈 목록")
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
