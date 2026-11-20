package com.mwt.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.mwt.tour.OptionCategory;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourLevelsResponse;
import com.mwt.tour.TourOptionsResponse;
import com.mwt.tour.TourService;
import com.mwt.tour.TourSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 음성으로 여행 상품 선택(MWT-UC5, 설계산출물 4.7·5.5). 고객과 여러 번 주고받는 대화로 테마, 상품, 투어 등급, 인원, 옵션을 고른다.
 * 서버는 대화를 저장하지 않는다 — 화면이 매번 대화 전체와 지금 고른 내용을 보내고, 여기서는 상품 목록을 모아 LlmClient에 넘긴 뒤
 * 돌아온 답을 그 목록으로 확인해 돌려준다. 신청은 화면이 신청 API로 한다.
 * OpenAI를 기다리는 동안 DB 연결을 잡지 않도록 트랜잭션을 두지 않는다(TourService의 메소드마다 자기 읽기 트랜잭션을 연다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class VoiceService {

	/** OpenAI의 할 말을 자르는 길이. 아래 고정 문장을 붙여도 시스템 줄 상한(1000자)을 넘지 않게 한다. */
	static final int MAX_REPLY_LENGTH = 800;

	static final String REMOVED_NOTICE = "말씀하신 것 가운데 고를 수 없는 것이 있어 빼 두었습니다. 다시 말씀해 주세요.";

	private final LlmClient llmClient;
	private final TourService tourService;

	VoiceChatResponse chat(VoiceChatRequest request) {
		List<VoiceMessage> messages = request.checkedMessages();
		VoiceContext context = new VoiceContext(tours(), request.draftOrEmpty());
		return check(llmClient.chat(context, messages), context);
	}

	/** 네 테마의 상품 목록(출발일이 오늘 뒤, 마감 포함)과 상품마다 고를 수 있는 등급·옵션. 옵션은 고를 수 있는 첫 등급으로 읽는다. */
	private List<VoiceContext.Tour> tours() {
		List<VoiceContext.Tour> tours = new ArrayList<>();
		for (Theme theme : Theme.values()) {
			for (TourSummary summary : tourService.getTours(theme)) {
				List<TourLevel> levels = tourService.getLevels(summary.tourId()).levels().stream()
					.map(TourLevelsResponse.Level::level)
					.toList();
				List<VoiceContext.Option> options = levels.isEmpty() ? List.of()
						: options(tourService.getOptions(summary.tourId(), levels.getFirst()));
				tours.add(new VoiceContext.Tour(summary.tourId(), theme, summary.title(), summary.departureDate(),
						summary.duration(), summary.remainingSeats(), summary.status(), levels, options));
			}
		}
		return tours;
	}

	private static List<VoiceContext.Option> options(TourOptionsResponse response) {
		return Stream.of(options(OptionCategory.HOTEL, response.hotels()),
				options(OptionCategory.TRANSPORT, response.transports()), options(OptionCategory.MEAL, response.meals()),
				options(OptionCategory.EXTRA, response.extras()))
			.flatMap(List::stream)
			.toList();
	}

	private static List<VoiceContext.Option> options(OptionCategory category, List<TourOptionsResponse.Option> options) {
		return options.stream().map(option -> new VoiceContext.Option(option.optionId(), category, option.name())).toList();
	}

	/**
	 * OpenAI의 답을 상품 목록으로 확인한다. 목록에 없는 상품, 그 상품(상품이 없으면 그 테마)에서 고를 수 없는 등급, 그 상품에 없는 옵션,
	 * 1 미만이거나 테마 규칙에 맞지 않는 인원은 비우거나 뺀다. 하나라도 비우거나 뺐으면 고객에게 알리고 신청으로 넘기지 않는다.
	 * 상품을 아직 고르지 않았을 때의 옵션은 상품마다 ID가 달라 비우지만, 고객이 고른 것을 뺀 것이 아니라 알리지 않는다.
	 * 남은 자리와 신청할 수 있는 상태는 보지 않는다 — 화면이 신청하기 전에 부르는 가격 계산이 거부한다.
	 */
	private static VoiceChatResponse check(VoiceChatResponse answer, VoiceContext context) {
		VoiceDraft draft = answer.draft();
		Theme theme = draft.theme();
		Long tourId = draft.tourId();
		TourLevel level = draft.level();
		Integer headcount = draft.headcount();
		List<Long> optionIds = draft.optionIds().stream().distinct().toList();
		List<String> removed = new ArrayList<>();

		VoiceContext.Tour tour = tourId == null ? null : context.findTour(tourId).orElse(null);
		if (tourId != null && tour == null) {
			removed.add("상품 " + tourId);
			tourId = null;
			level = null;
			optionIds = List.of();
		}
		if (tour != null) {
			theme = tour.theme();
			if (level != null && !tour.levels().contains(level)) {
				removed.add("등급 " + level);
				level = null;
			}
			List<Long> kept = optionIds.stream().filter(tour::hasOption).toList();
			if (kept.size() < optionIds.size()) {
				removed.add("옵션 " + optionIds.stream().filter(optionId -> !kept.contains(optionId)).toList());
			}
			optionIds = kept;
		} else {
			if (level != null && theme != null && !theme.allows(level)) {
				removed.add("등급 " + level);
				level = null;
			}
			optionIds = List.of();
		}
		if (headcount != null && (headcount < 1 || theme != null && !theme.allowsHeadcount(headcount))) {
			removed.add("인원 " + headcount);
			headcount = null;
		}

		String reply = truncate(answer.reply());
		VoiceNext next = answer.next();
		if (!removed.isEmpty()) {
			log.warn("OpenAI가 고른 값 가운데 고를 수 없는 것을 뺐습니다: {}", removed);
			if (next == VoiceNext.TALK) {
				reply = reply + " " + REMOVED_NOTICE;
			} else if (next == VoiceNext.APPLY) {
				next = VoiceNext.TALK;
				reply = REMOVED_NOTICE;
			}
		}
		if (next == VoiceNext.APPLY) {
			List<String> missing = new ArrayList<>();
			if (tourId == null) {
				missing.add("여행 상품");
			}
			if (level == null) {
				missing.add("투어 등급");
			}
			if (headcount == null) {
				missing.add("인원");
			}
			if (!missing.isEmpty()) {
				next = VoiceNext.TALK;
				reply = "아직 정하지 않은 것이 있습니다. " + String.join(", ", missing) + "을 말씀해 주세요.";
			}
		}
		return new VoiceChatResponse(reply, new VoiceDraft(theme, tourId, level, headcount, optionIds), next);
	}

	/** 코드포인트로 센다 — 시스템 줄의 상한도 코드포인트로 센다(VoiceChatRequest). */
	private static String truncate(String reply) {
		if (reply.codePointCount(0, reply.length()) <= MAX_REPLY_LENGTH) {
			return reply;
		}
		return reply.substring(0, reply.offsetByCodePoints(0, MAX_REPLY_LENGTH));
	}

}
