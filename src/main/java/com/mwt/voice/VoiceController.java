package com.mwt.voice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 음성에서 나온 글자를 받아 선택 항목으로 바꿔 달라는 고객의 요청을 받는다(설계산출물 v0.5 4.7, 4.9 서버 API 표).
 * OpenAI를 부르지 못하면 503 {"message"}다 — 화면은 그때 손으로 고르라고 안내한다(5.5 부 흐름).
 */
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

	private final VoiceService voiceService;

	@PostMapping("/parse")
	public TourSelection parse(@Valid @RequestBody VoiceParseRequest request) {
		return voiceService.parse(request.text());
	}

}
