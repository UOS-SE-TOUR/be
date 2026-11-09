package com.mwt.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourService;
import com.mwt.tour.TourStatus;
import com.mwt.tour.TourSummary;

/**
 * 음성으로 여행 상품 선택(MWT-UC5)의 서버 흐름. OpenAI 대신 가짜 LlmClient를 쓰고, 후보 상품 찾기(findTours)는 흉내 낸다
 * (findTours의 날짜 규칙은 tour 모듈의 TourServiceTest가 본다).
 */
class VoiceServiceTest {

	private static final LocalDate DECEMBER_FIRST = LocalDate.of(2026, 12, 1);

	private final TourService tourService = mock(TourService.class);

	@Test
	void 뽑은_테마와_출발일로_후보_상품을_찾아_함께_돌려준다() {
		FakeLlmClient llmClient = FakeLlmClient.answering(
				TourSelection.extracted(Theme.GOLF, DECEMBER_FIRST, TourLevel.GRAND, 2));
		TourSummary candidate = new TourSummary(3L, "골프 챌린지 투어 12/1 출발 5일", DECEMBER_FIRST, 5, 8,
				TourStatus.RECRUITING);
		when(tourService.findTours(Theme.GOLF, DECEMBER_FIRST)).thenReturn(List.of(candidate));

		TourSelection selection = new VoiceService(llmClient, tourService).parse("12월 1일 골프 그랜드로 둘이요");

		assertThat(selection)
			.isEqualTo(new TourSelection(Theme.GOLF, DECEMBER_FIRST, TourLevel.GRAND, 2, List.of(candidate)));
		assertThat(llmClient.texts()).containsExactly("12월 1일 골프 그랜드로 둘이요");
	}

	@Test
	void 뽑지_못한_항목은_비워_두고_후보는_뽑은_테마와_출발일로만_찾는다() {
		FakeLlmClient llmClient = FakeLlmClient.answering(TourSelection.extracted(Theme.TREKKING, null, null, null));
		when(tourService.findTours(Theme.TREKKING, null)).thenReturn(List.of());

		TourSelection selection = new VoiceService(llmClient, tourService).parse("등산 가고 싶어요");

		assertThat(selection).isEqualTo(new TourSelection(Theme.TREKKING, null, null, null, List.of()));
		verify(tourService).findTours(Theme.TREKKING, null);
	}

	@Test
	void OpenAI를_부르지_못하면_그_오류를_그대로_던지고_후보를_찾지_않는다() {
		ResponseStatusException failure = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "시간 초과");
		VoiceService voiceService = new VoiceService(FakeLlmClient.failing(failure), tourService);

		assertThatThrownBy(() -> voiceService.parse("골프 여행")).isSameAs(failure);
		verifyNoInteractions(tourService);
	}

}
