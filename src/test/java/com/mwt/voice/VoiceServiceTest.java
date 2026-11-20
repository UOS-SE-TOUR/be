package com.mwt.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.mwt.tour.OptionCategory;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourLevelsResponse;
import com.mwt.tour.TourOptionsResponse;
import com.mwt.tour.TourService;
import com.mwt.tour.TourStatus;
import com.mwt.tour.TourSummary;

/**
 * 음성 대화(MWT-UC5)의 서버 흐름: 대화와 상품 목록을 LlmClient에 넘기고, 돌아온 답을 그 목록으로 확인한다.
 * OpenAI 대신 가짜 LlmClient를 쓰고 상품 읽기(TourService)는 흉내 낸다. 입력 검증은 VoiceControllerTest가 본다.
 * 상품 목록: 효도 12(그랜드·프리미엄, 옵션 101~107), 허니문 21(그랜드·프리미엄, 옵션 201), 트레킹 31(세 등급, 옵션 301).
 */
class VoiceServiceTest {

	private static final LocalDate DECEMBER_FIFTH = LocalDate.of(2026, 12, 5);

	private static final List<VoiceMessage> CONVERSATION = List.of(
			new VoiceMessage(VoiceSpeaker.SYSTEM, "안녕하세요, 최유성 고객님. 어떤 여행을 찾으세요?"),
			new VoiceMessage(VoiceSpeaker.CUSTOMER, "추천해 주세요"));

	private static final String REPLY = "어떤 날을 기념하시나요?";

	private final TourService tourService = mock(TourService.class);

	@BeforeEach
	void setUp() {
		when(tourService.getTours(Theme.PARENTS)).thenReturn(List.of(
				new TourSummary(12L, "효도 힐링 투어 12/5 출발 5일", DECEMBER_FIFTH, 5, 8, TourStatus.RECRUITING)));
		when(tourService.getLevels(12L)).thenReturn(levels(TourLevel.GRAND, TourLevel.PREMIUM));
		when(tourService.getOptions(12L, TourLevel.GRAND)).thenReturn(new TourOptionsResponse(
				List.of(option(101, "4등급 호텔"), option(102, "5등급 호텔")),
				List.of(option(103, "항공 직항")),
				List.of(option(104, "현지식 레스토랑"), option(105, "고급 레스토랑")),
				List.of(option(106, "샴페인"), option(107, "커피"))));
		when(tourService.getTours(Theme.HONEYMOON)).thenReturn(List.of(
				new TourSummary(21L, "허니문 낭만 투어 12/10 출발 6일", LocalDate.of(2026, 12, 10), 6, 0, TourStatus.FULL)));
		when(tourService.getLevels(21L)).thenReturn(levels(TourLevel.GRAND, TourLevel.PREMIUM));
		when(tourService.getOptions(21L, TourLevel.GRAND)).thenReturn(new TourOptionsResponse(
				List.of(option(201, "4등급 호텔")), List.of(), List.of(), List.of()));
		when(tourService.getTours(Theme.TREKKING)).thenReturn(List.of(
				new TourSummary(31L, "아웃도어 트레킹 투어 12/1 출발 4일", LocalDate.of(2026, 12, 1), 4, 10,
						TourStatus.CONFIRMED)));
		when(tourService.getLevels(31L)).thenReturn(levels(TourLevel.CLASSIC, TourLevel.GRAND, TourLevel.PREMIUM));
		when(tourService.getOptions(31L, TourLevel.CLASSIC)).thenReturn(new TourOptionsResponse(
				List.of(), List.of(), List.of(option(301, "고급 레스토랑")), List.of()));
	}

	@Test
	void 대화_전체와_상품_목록과_지금_고른_내용을_넘기고_상품_목록에_가격은_없다() {
		VoiceDraft draft = new VoiceDraft(Theme.PARENTS, null, null, null, List.of());
		FakeLlmClient llmClient = FakeLlmClient.answering(talk(draft));
		List<VoiceMessage> sent = List.of(CONVERSATION.getFirst(),
				new VoiceMessage(VoiceSpeaker.CUSTOMER, "  추천해 주세요\n"));

		VoiceChatResponse response = service(llmClient).chat(new VoiceChatRequest(sent, draft));

		assertThat(response).isEqualTo(talk(draft));
		assertThat(llmClient.conversations()).containsExactly(CONVERSATION);
		VoiceContext context = llmClient.contexts().getFirst();
		assertThat(context.draft()).isEqualTo(draft);
		assertThat(context.tours()).extracting(VoiceContext.Tour::tourId).containsExactly(21L, 12L, 31L);
		assertThat(context.findTour(12L)).contains(new VoiceContext.Tour(12L, Theme.PARENTS, "효도 힐링 투어 12/5 출발 5일",
				DECEMBER_FIFTH, 5, 8, TourStatus.RECRUITING, List.of(TourLevel.GRAND, TourLevel.PREMIUM), List.of(
						new VoiceContext.Option(101L, OptionCategory.HOTEL, "4등급 호텔"),
						new VoiceContext.Option(102L, OptionCategory.HOTEL, "5등급 호텔"),
						new VoiceContext.Option(103L, OptionCategory.TRANSPORT, "항공 직항"),
						new VoiceContext.Option(104L, OptionCategory.MEAL, "현지식 레스토랑"),
						new VoiceContext.Option(105L, OptionCategory.MEAL, "고급 레스토랑"),
						new VoiceContext.Option(106L, OptionCategory.EXTRA, "샴페인"),
						new VoiceContext.Option(107L, OptionCategory.EXTRA, "커피"))));
		assertThat(context.findTour(21L)).get().extracting(VoiceContext.Tour::status).isEqualTo(TourStatus.FULL);
		assertThat(componentNames(VoiceContext.Tour.class)).noneMatch(name -> name.toLowerCase().contains("price"));
		assertThat(componentNames(VoiceContext.Option.class)).noneMatch(name -> name.toLowerCase().contains("price"));
		verify(tourService).getOptions(31L, TourLevel.CLASSIC);
	}

	@Test
	void draft가_없으면_아무것도_고르지_않은_것으로_넘긴다() {
		FakeLlmClient llmClient = FakeLlmClient.answering(talk(VoiceDraft.EMPTY));

		service(llmClient).chat(new VoiceChatRequest(CONVERSATION, null));

		assertThat(llmClient.contexts().getFirst().draft()).isEqualTo(VoiceDraft.EMPTY);
	}

	@Test
	void 고를_수_있는_값은_그대로_두고_테마는_상품의_테마로_맞추고_같은_옵션은_한_번만_둔다() {
		VoiceChatResponse response = chat(talk(new VoiceDraft(Theme.HONEYMOON, 12L, TourLevel.PREMIUM, 2,
				List.of(105L, 106L, 105L))));

		assertThat(response).isEqualTo(talk(new VoiceDraft(Theme.PARENTS, 12L, TourLevel.PREMIUM, 2,
				List.of(105L, 106L))));
	}

	@Test
	void 목록에_없는_상품은_등급과_옵션도_비우고_고정_문장을_붙인다() {
		VoiceChatResponse response = chat(talk(new VoiceDraft(Theme.GOLF, 999L, TourLevel.GRAND, 2, List.of(101L))));

		assertThat(response).isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
				new VoiceDraft(Theme.GOLF, null, null, 2, List.of()), VoiceNext.TALK));
	}

	@Test
	void 그_상품에서_고를_수_없는_등급은_비운다() {
		VoiceChatResponse response = chat(talk(new VoiceDraft(Theme.PARENTS, 12L, TourLevel.CLASSIC, 3, List.of())));

		assertThat(response).isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
				new VoiceDraft(Theme.PARENTS, 12L, null, 3, List.of()), VoiceNext.TALK));
	}

	@Test
	void 그_상품에_없는_옵션은_뺀다() {
		VoiceChatResponse response = chat(talk(new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3,
				List.of(101L, 301L))));

		assertThat(response).isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3, List.of(101L)), VoiceNext.TALK));
	}

	@Test
	void 호텔_교통_식사를_종류마다_둘_이상_고르면_그_종류를_비우고_추가_옵션은_둔다() {
		VoiceChatResponse response = chat(new VoiceChatResponse("신청하겠습니다.",
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3, List.of(101L, 105L, 102L, 106L, 107L)),
				VoiceNext.APPLY));

		assertThat(response).isEqualTo(new VoiceChatResponse(VoiceService.REMOVED_NOTICE,
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3, List.of(105L, 106L, 107L)), VoiceNext.TALK));
	}

	@Test
	void 허니문에_2명이_아니면_상품이_없어도_인원을_비운다() {
		assertThat(chat(talk(new VoiceDraft(Theme.HONEYMOON, null, null, 3, List.of()))))
			.isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
					new VoiceDraft(Theme.HONEYMOON, null, null, null, List.of()), VoiceNext.TALK));
		assertThat(chat(talk(new VoiceDraft(Theme.HONEYMOON, 21L, TourLevel.GRAND, 3, List.of()))))
			.isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
					new VoiceDraft(Theme.HONEYMOON, 21L, TourLevel.GRAND, null, List.of()), VoiceNext.TALK));
	}

	@Test
	void 인원이_1_미만이면_비운다() {
		assertThat(chat(talk(new VoiceDraft(Theme.GOLF, null, null, 0, List.of()))))
			.isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
					new VoiceDraft(Theme.GOLF, null, null, null, List.of()), VoiceNext.TALK));
	}

	@Test
	void 비우거나_뺀_값이_있으면_신청으로_넘기지_않고_고정_문장으로_바꾼다() {
		VoiceChatResponse response = chat(new VoiceChatResponse("신청하겠습니다.",
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 2, List.of(301L)), VoiceNext.APPLY));

		assertThat(response).isEqualTo(new VoiceChatResponse(VoiceService.REMOVED_NOTICE,
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 2, List.of()), VoiceNext.TALK));
	}

	@Test
	void 그만두는_답은_값만_거르고_말은_그대로_둔다() {
		VoiceChatResponse response = chat(new VoiceChatResponse("다음에 또 찾아 주세요.",
				new VoiceDraft(Theme.PARENTS, 999L, null, null, List.of()), VoiceNext.STOP));

		assertThat(response).isEqualTo(new VoiceChatResponse("다음에 또 찾아 주세요.",
				new VoiceDraft(Theme.PARENTS, null, null, null, List.of()), VoiceNext.STOP));
	}

	@Test
	void 상품_없이_온_테마_등급_인원은_테마_규칙에_맞으면_남긴다() {
		VoiceDraft draft = new VoiceDraft(Theme.HONEYMOON, null, TourLevel.PREMIUM, 2, List.of());

		assertThat(chat(talk(draft))).isEqualTo(talk(draft));
	}

	@Test
	void 테마만_온_답은_그대로다() {
		VoiceDraft draft = new VoiceDraft(Theme.TREKKING, null, null, null, List.of());

		assertThat(chat(talk(draft))).isEqualTo(talk(draft));
	}

	@Test
	void 등급만_온_답은_테마를_모르니_그대로다() {
		VoiceDraft draft = new VoiceDraft(null, null, TourLevel.CLASSIC, null, List.of());

		assertThat(chat(talk(draft))).isEqualTo(talk(draft));
	}

	@ParameterizedTest
	@EnumSource(value = Theme.class, names = { "HONEYMOON", "PARENTS" })
	void 상품_없이_허니문과_효도의_클래식이_오면_등급을_비운다(Theme theme) {
		assertThat(chat(talk(new VoiceDraft(theme, null, TourLevel.CLASSIC, 2, List.of()))))
			.isEqualTo(new VoiceChatResponse(REPLY + " " + VoiceService.REMOVED_NOTICE,
					new VoiceDraft(theme, null, null, 2, List.of()), VoiceNext.TALK));
	}

	@Test
	void 상품_없이_온_옵션은_비우지만_알리지_않는다() {
		assertThat(chat(talk(new VoiceDraft(Theme.TREKKING, null, TourLevel.CLASSIC, null, List.of(305L, 306L)))))
			.isEqualTo(talk(new VoiceDraft(Theme.TREKKING, null, TourLevel.CLASSIC, null, List.of())));
	}

	@Test
	void 빈_칸이_있는데_신청하려_하면_빈_칸을_묻는다() {
		VoiceChatResponse response = chat(new VoiceChatResponse("신청하겠습니다.",
				new VoiceDraft(Theme.PARENTS, 12L, null, null, List.of()), VoiceNext.APPLY));

		assertThat(response).isEqualTo(new VoiceChatResponse("아직 정하지 않은 것이 있습니다. 투어 등급, 인원을 말씀해 주세요.",
				new VoiceDraft(Theme.PARENTS, 12L, null, null, List.of()), VoiceNext.TALK));
		assertThat(chat(new VoiceChatResponse("신청하겠습니다.", VoiceDraft.EMPTY, VoiceNext.APPLY)).reply())
			.isEqualTo("아직 정하지 않은 것이 있습니다. 여행 상품, 투어 등급, 인원을 말씀해 주세요.");
	}

	@Test
	void 다_골랐으면_신청으로_넘긴다() {
		VoiceChatResponse answer = new VoiceChatResponse("신청하겠습니다.",
				new VoiceDraft(Theme.PARENTS, 12L, TourLevel.GRAND, 3, List.of(105L, 106L)), VoiceNext.APPLY);

		assertThat(chat(answer)).isEqualTo(answer);
	}

	@Test
	void 할_말이_800자를_넘으면_자르고_고정_문장을_붙여도_1000자를_넘지_않는다() {
		String longReply = "😀".repeat(900);   // 코드포인트 하나가 UTF-16 두 자다

		String kept = chat(new VoiceChatResponse(longReply, VoiceDraft.EMPTY, VoiceNext.TALK)).reply();
		String noticed = chat(new VoiceChatResponse(longReply, new VoiceDraft(Theme.GOLF, null, null, 0, List.of()),
				VoiceNext.TALK)).reply();

		assertThat(kept).isEqualTo("😀".repeat(VoiceService.MAX_REPLY_LENGTH));
		assertThat(noticed).isEqualTo(kept + " " + VoiceService.REMOVED_NOTICE);
		assertThat(noticed.codePointCount(0, noticed.length())).isLessThanOrEqualTo(VoiceChatRequest.MAX_SYSTEM_LENGTH);
	}

	@Test
	void OpenAI를_부르지_못하면_그_오류를_그대로_던진다() {
		ResponseStatusException failure = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "시간 초과");
		VoiceService voiceService = service(FakeLlmClient.failing(failure));

		assertThatThrownBy(() -> voiceService.chat(new VoiceChatRequest(CONVERSATION, null))).isSameAs(failure);
	}

	private VoiceService service(FakeLlmClient llmClient) {
		return new VoiceService(llmClient, tourService);
	}

	private VoiceChatResponse chat(VoiceChatResponse answer) {
		return service(FakeLlmClient.answering(answer)).chat(new VoiceChatRequest(CONVERSATION, null));
	}

	private static VoiceChatResponse talk(VoiceDraft draft) {
		return new VoiceChatResponse(REPLY, draft, VoiceNext.TALK);
	}

	private static TourLevelsResponse levels(TourLevel... levels) {
		return new TourLevelsResponse("서비스", "기념품", "차량", Arrays.stream(levels)
			.map(level -> new TourLevelsResponse.Level(level, level.getDisplayName(), 1_000_000, level.getHotelGrade(),
					level.getMeal(), level.isIncludesChampagne()))
			.toList());
	}

	private static TourOptionsResponse.Option option(long optionId, String name) {
		return new TourOptionsResponse.Option(optionId, name, 50_000);
	}

	private static List<String> componentNames(Class<? extends Record> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

}
