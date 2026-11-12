package com.mwt.voice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 음성인식(Web Speech API)이 돌려준 글자다(설계산출물 5.5 parse(request)).
 * 글자 수 상한은 한 번에 말하는 길이보다 넉넉하게 두되, OpenAI로 보내는 양을 묶는다.
 */
@Schema(description = "음성으로 고르기 입력")
public record VoiceParseRequest(
		@Schema(description = "브라우저 음성 인식(Web Speech API)이 돌려준 글자. 300자 이하")
		@NotBlank(message = "음성에서 알아들은 글자가 없습니다. 다시 말해 주세요.")
		@Size(max = 300, message = "말이 너무 깁니다. 300자 안으로 다시 말해 주세요.") String text) {
}
