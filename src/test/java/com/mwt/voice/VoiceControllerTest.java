package com.mwt.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import com.mwt.ClockConfig;
import com.mwt.common.security.JwtTokenProvider;
import com.mwt.common.security.SecurityConfig;
import com.mwt.member.MemberType;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/voice/chat(설계산출물 4.9 서버 API 표, 5.5)의 입력 검증과 응답 모양. VoiceService는 그대로 쓰고
 * LlmClient와 상품 읽기(TourService)는 흉내 낸다. 답을 상품 목록으로 거르는 규칙은 VoiceServiceTest가 본다.
 */
@WebMvcTest(controllers = VoiceController.class)
@Import({ SecurityConfig.class, JwtTokenProvider.class, ClockConfig.class, VoiceService.class })
class VoiceControllerTest {

	private static final VoiceMessage GREETING = new VoiceMessage(VoiceSpeaker.SYSTEM, "안녕하세요. 어떤 여행을 찾으세요?");

	private static final VoiceChatResponse ANSWER = new VoiceChatResponse("트레킹 상품은 12월 1일 출발 4일짜리가 있습니다.",
			new VoiceDraft(Theme.TREKKING, null, TourLevel.CLASSIC, null, List.of()), VoiceNext.TALK);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	LlmClient llmClient;

	@MockitoBean
	TourService tourService;

	@Test
	void 할_말과_고른_내용과_다음에_할_일을_돌려준다() throws Exception {
		when(llmClient.chat(any(), anyList())).thenReturn(ANSWER);

		mockMvc.perform(chat("""
				{"messages": [{"speaker": "SYSTEM", "text": "안녕하세요. 어떤 여행을 찾으세요?"},
				              {"speaker": "CUSTOMER", "text": "트레킹 클래식이요"}],
				 "draft": {"theme": "TREKKING", "tourId": null, "level": null, "headcount": null, "optionIds": []}}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"reply": "트레킹 상품은 12월 1일 출발 4일짜리가 있습니다.",
					 "draft": {"theme": "TREKKING", "tourId": null, "level": "CLASSIC", "headcount": null, "optionIds": []},
					 "next": "TALK"}""", JsonCompareMode.STRICT));

		ArgumentCaptor<VoiceContext> context = ArgumentCaptor.forClass(VoiceContext.class);
		verify(llmClient).chat(context.capture(), anyList());
		assertThat(context.getValue().draft()).isEqualTo(new VoiceDraft(Theme.TREKKING, null, null, null, List.of()));
	}

	@Test
	void draft를_빼면_아무것도_고르지_않은_것으로_본다() throws Exception {
		when(llmClient.chat(any(), anyList())).thenReturn(ANSWER);

		mockMvc.perform(chat("""
				{"messages": [{"speaker": "CUSTOMER", "text": "추천해 주세요"}]}""")).andExpect(status().isOk());

		ArgumentCaptor<VoiceContext> context = ArgumentCaptor.forClass(VoiceContext.class);
		verify(llmClient).chat(context.capture(), anyList());
		assertThat(context.getValue().draft()).isEqualTo(VoiceDraft.EMPTY);
	}

	@Test
	void 대화가_60줄을_넘으면_400() throws Exception {
		when(llmClient.chat(any(), anyList())).thenReturn(ANSWER);
		mockMvc.perform(chat(conversation(60))).andExpect(status().isOk());
		clearInvocations(llmClient, tourService);

		expectBadRequest(conversation(61), VoiceChatRequest.TOO_MANY_MESSAGES);
	}

	@Test
	void 고객_줄이_300자를_넘으면_400() throws Exception {
		when(llmClient.chat(any(), anyList())).thenReturn(ANSWER);
		mockMvc.perform(chat(body(GREETING, customer("가".repeat(300))))).andExpect(status().isOk());
		clearInvocations(llmClient, tourService);

		expectBadRequest(body(GREETING, customer("가".repeat(301))), VoiceChatRequest.TOO_LONG_SPEECH);
	}

	@Test
	void 시스템_줄이_1000자를_넘으면_400() throws Exception {
		when(llmClient.chat(any(), anyList())).thenReturn(ANSWER);
		mockMvc.perform(chat(body(system("가".repeat(1000)), customer("네")))).andExpect(status().isOk());
		clearInvocations(llmClient, tourService);

		expectBadRequest(body(system("가".repeat(1001)), customer("네")), VoiceChatRequest.CANNOT_CONTINUE);
	}

	@Test
	void 이어_갈_수_없는_대화는_400() throws Exception {
		List<String> bodies = List.of(
				"{}",
				"{\"messages\": []}",
				"{\"messages\": null}",
				body(GREETING),
				body(GREETING, customer("네"), system("좋습니다.")),
				body(GREETING, customer("   ")),
				body(GREETING, customer(null)),
				body(GREETING, new VoiceMessage(null, "네")),
				body(GREETING, null, customer("네")));
		for (String body : bodies) {
			expectBadRequest(body, VoiceChatRequest.CANNOT_CONTINUE);
		}
	}

	@Test
	void OpenAI를_부르지_못하면_503과_일반_문구() throws Exception {
		when(llmClient.chat(any(), anyList()))
			.thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 호출에 실패했습니다"));

		mockMvc.perform(chat(body(GREETING, customer("골프 여행"))))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().json("{\"message\": \"서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.\"}",
					JsonCompareMode.STRICT));
	}

	@Test
	void 로그인하지_않으면_401이고_직원이면_403() throws Exception {
		String body = body(GREETING, customer("골프 여행"));
		mockMvc.perform(post("/api/v1/voice/chat").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/voice/chat").cookie(cookie(MemberType.STAFF)).contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isForbidden());
		verifyNoInteractions(llmClient, tourService);
	}

	private void expectBadRequest(String body, String message) throws Exception {
		mockMvc.perform(chat(body))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": %s}".formatted(jsonMapper.writeValueAsString(message)),
					JsonCompareMode.STRICT));
		verifyNoInteractions(llmClient, tourService);
	}

	/** 인사로 시작해 고객 줄로 끝나는 size줄 대화. */
	private String conversation(int size) {
		List<VoiceMessage> messages = new ArrayList<>();
		IntStream.range(0, size).forEach(i -> messages.add((size - i) % 2 == 1 ? customer("네") : system("좋습니다.")));
		return jsonMapper.writeValueAsString(new VoiceChatRequest(messages, null));
	}

	private String body(VoiceMessage... messages) {
		return jsonMapper.writeValueAsString(new VoiceChatRequest(Arrays.asList(messages), null));
	}

	private static VoiceMessage customer(String text) {
		return new VoiceMessage(VoiceSpeaker.CUSTOMER, text);
	}

	private static VoiceMessage system(String text) {
		return new VoiceMessage(VoiceSpeaker.SYSTEM, text);
	}

	private MockHttpServletRequestBuilder chat(String body) {
		return post("/api/v1/voice/chat").cookie(cookie(MemberType.CUSTOMER)).contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private Cookie cookie(MemberType memberType) {
		return new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, memberType.name()));
	}

}
