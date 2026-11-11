package com.mwt.voice;

import org.springframework.stereotype.Service;

import com.mwt.tour.TourService;

import lombok.RequiredArgsConstructor;

/**
 * 음성으로 여행 상품 선택(MWT-UC5, 설계산출물 4.7·5.5). LlmClient로 글자에서 테마, 출발일, 투어 등급, 인원을 뽑고,
 * TourService.findTours로 테마와 출발일이 맞는 후보 상품을 찾아 함께 돌려준다.
 * OpenAI를 기다리는 동안 DB 연결을 잡지 않도록 트랜잭션을 두지 않는다(findTours가 자기 읽기 트랜잭션을 연다).
 */
@Service
@RequiredArgsConstructor
class VoiceService {

	private final LlmClient llmClient;
	private final TourService tourService;

	TourSelection parse(String text) {
		TourSelection selection = llmClient.extractSelection(text);
		return selection.withCandidates(tourService.findTours(selection.theme(), selection.departureDate()));
	}

}
