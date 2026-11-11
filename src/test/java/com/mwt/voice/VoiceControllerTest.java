package com.mwt.voice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
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
import com.mwt.tour.TourStatus;
import com.mwt.tour.TourSummary;

import jakarta.servlet.http.Cookie;

/**
 * POST /api/v1/voice/parse(설계산출물 4.9 서버 API 표, 5.5)의 요청 검증과 응답 모양. VoiceService는 흉내 낸다.
 * 주소별 권한 규칙은 common.security의 SecurityConfigTest가 따로 본다.
 */
@WebMvcTest(controllers = VoiceController.class)
@Import({ SecurityConfig.class, JwtTokenProvider.class, ClockConfig.class })
class VoiceControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	VoiceService voiceService;

	@Test
	void 뽑은_내용과_후보_상품을_돌려준다() throws Exception {
		LocalDate date = LocalDate.of(2026, 12, 1);
		TourSummary candidate = new TourSummary(3L, "골프 챌린지 투어 12/1 출발 5일", date, 5, 8, TourStatus.RECRUITING);
		when(voiceService.parse("12월 1일 골프 그랜드로 둘이요"))
			.thenReturn(new TourSelection(Theme.GOLF, date, TourLevel.GRAND, 2, List.of(candidate)));

		mockMvc.perform(parse("{\"text\": \"12월 1일 골프 그랜드로 둘이요\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"theme": "GOLF", "departureDate": "2026-12-01", "level": "GRAND", "headcount": 2,
					 "candidates": [{"tourId": 3, "title": "골프 챌린지 투어 12/1 출발 5일", "departureDate": "2026-12-01",
					                 "duration": 5, "remainingSeats": 8, "status": "RECRUITING"}]}""",
					JsonCompareMode.STRICT));
	}

	@Test
	void 뽑지_못한_항목은_null이고_후보가_없으면_빈_목록() throws Exception {
		when(voiceService.parse("안녕하세요")).thenReturn(TourSelection.extracted(null, null, null, null));

		mockMvc.perform(parse("{\"text\": \"안녕하세요\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"theme": null, "departureDate": null, "level": null, "headcount": null, "candidates": []}""",
					JsonCompareMode.STRICT));
	}

	@Test
	void 글자가_없으면_400() throws Exception {
		for (String body : new String[] { "{}", "{\"text\": \"\"}", "{\"text\": \"   \"}" }) {
			mockMvc.perform(parse(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("{\"message\": \"음성에서 알아들은 글자가 없습니다. 다시 말해 주세요.\"}",
						JsonCompareMode.STRICT));
		}
		verifyNoInteractions(voiceService);
	}

	@Test
	void 글자가_300자를_넘으면_400() throws Exception {
		mockMvc.perform(parse("{\"text\": \"%s\"}".formatted("가".repeat(300))))
			.andExpect(status().isOk());
		mockMvc.perform(parse("{\"text\": \"%s\"}".formatted("가".repeat(301))))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"말이 너무 깁니다. 300자 안으로 다시 말해 주세요.\"}",
					JsonCompareMode.STRICT));
	}

	@Test
	void OpenAI를_부르지_못하면_503과_일반_문구() throws Exception {
		when(voiceService.parse(anyString()))
			.thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 호출에 실패했습니다"));

		mockMvc.perform(parse("{\"text\": \"골프 여행\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().json("{\"message\": \"서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.\"}",
					JsonCompareMode.STRICT));
	}

	private MockHttpServletRequestBuilder parse(String body) {
		Cookie customer = new Cookie(JwtTokenProvider.COOKIE_NAME,
				jwtTokenProvider.createToken(1, MemberType.CUSTOMER.name()));
		return post("/api/v1/voice/parse").cookie(customer).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
