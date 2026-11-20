package com.mwt.voice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 음성 대화를 이어 가 달라는 고객의 요청을 받는다(설계산출물 4.7, 4.9 서버 API 표).
 * OpenAI를 부르지 못하면 503 {"message"}다 — 화면은 그때 손으로 고르라고 안내한다(5.5 부 흐름).
 */
@Tag(name = "음성으로 고르기")
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

	private final VoiceService voiceService;

	@Operation(summary = "음성 대화 이어 가기 (고객)",
			description = "지금까지의 대화와 지금 고른 내용을 받아 시스템이 할 말, 이번 말까지 반영한 고른 내용, 다음에 할 일을 돌려준다. "
					+ "서버는 대화를 저장하지 않으므로 화면이 매번 대화 전체를 보낸다. 다음에 할 일이 APPLY면 화면이 고른 내용으로 신청한다.")
	@ApiResponse(responseCode = "200", description = "시스템이 할 말과 고른 내용, 다음에 할 일")
	@ApiResponse(responseCode = "400", description = "대화가 60줄을 넘음, 고객 줄이 300자를 넘음, 그 밖에 이어 갈 수 없는 대화"
			+ "(줄이 없음, 빈 줄, 1000자를 넘는 시스템 줄, 마지막 줄이 고객의 말이 아님). 화면은 문구를 보이고 직접 고르게 한다")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_CUSTOMER)
	@ApiResponse(responseCode = "503", description = "음성으로 고르기를 지금 쓸 수 없음(OpenAI를 부르지 못했거나 답을 읽지 못함). 화면은 조건을 직접 고르게 한다")
	@PostMapping("/chat")
	public VoiceChatResponse chat(@RequestBody VoiceChatRequest request) {
		return voiceService.chat(request);
	}

}
