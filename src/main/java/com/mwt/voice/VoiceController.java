package com.mwt.voice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 음성에서 나온 글자를 받아 선택 항목으로 바꿔 달라는 고객의 요청을 받는다(설계산출물 v0.5 4.7, 4.9 서버 API 표).
 * OpenAI를 부르지 못하면 503 {"message"}다 — 화면은 그때 손으로 고르라고 안내한다(5.5 부 흐름).
 */
@Tag(name = "음성으로 고르기")
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

	private final VoiceService voiceService;

	@Operation(summary = "음성으로 고르기 (고객)",
			description = "브라우저 음성 인식(Web Speech API)이 돌려준 글자를 받아 테마·출발일·투어 등급·인원과 후보 상품을 돌려준다. 화면은 이 값으로 조건 칸을 채우고, 고객이 고친 뒤 가격 계산으로 넘어간다.")
	@ApiResponse(responseCode = "200", description = "뽑은 조건과 후보 상품. 알아듣지 못한 조건은 null")
	@ApiResponse(responseCode = "400", description = "글자가 비었거나 300자를 넘음")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_CUSTOMER)
	@ApiResponse(responseCode = "503", description = "음성으로 고르기를 지금 쓸 수 없음(OpenAI를 부르지 못했거나 답을 읽지 못함). 화면은 조건을 직접 고르게 한다")
	@PostMapping("/parse")
	public TourSelection parse(@Valid @RequestBody VoiceParseRequest request) {
		return voiceService.parse(request.text());
	}

}
