package com.mwt.voice;

import java.util.List;

import com.mwt.common.exception.BusinessException;
import com.mwt.common.text.TextInput;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화를 이어 갈 입력이다. 서버는 대화를 저장하지 않으므로 화면이 매번 대화 전체와 지금 고른 내용을 보낸다.
 * 줄마다 말한 쪽에 따라 글자 수 상한이 달라 검증 애너테이션 대신 {@link #checkedMessages()}가 확인한다.
 */
@Schema(description = "음성 대화 입력")
public record VoiceChatRequest(
		@Schema(description = "지금까지의 대화. 첫 줄은 화면이 만든 인사이고 마지막 줄은 고객이 방금 한 말이다. 1~60줄")
		List<VoiceMessage> messages,
		@Schema(description = "지금까지 고른 내용. 빼거나 null이면 아무것도 고르지 않은 것으로 본다", nullable = true)
		VoiceDraft draft) {

	static final int MAX_MESSAGES = 60;
	static final int MAX_CUSTOMER_LENGTH = 300;
	static final int MAX_SYSTEM_LENGTH = 1000;

	static final String TOO_MANY_MESSAGES = "대화가 너무 길어졌습니다. 화면에서 직접 골라 주세요.";
	static final String TOO_LONG_SPEECH = "말이 너무 깁니다. 화면에서 직접 골라 주세요.";
	static final String CANNOT_CONTINUE = "음성 대화를 이어 갈 수 없습니다. 화면에서 직접 골라 주세요.";

	/**
	 * 대화를 확인하고 줄마다 앞뒤 공백을 뗀 대화를 돌려준다. 글자 수는 다듬은 말을 코드포인트로 센다(TextInput).
	 * 줄이 없거나, 60줄을 넘거나, 빈 줄·말한 쪽이 없는 줄이 있거나, 줄이 상한(고객 300자, 시스템 1000자)을 넘거나,
	 * 마지막 줄이 고객의 말이 아니면 400이다. 문구는 화면이 그대로 보인다.
	 */
	List<VoiceMessage> checkedMessages() {
		if (messages == null || messages.isEmpty()) {
			throw new BusinessException(CANNOT_CONTINUE);
		}
		if (messages.size() > MAX_MESSAGES) {
			throw new BusinessException(TOO_MANY_MESSAGES);
		}
		List<VoiceMessage> checked = messages.stream().map(VoiceChatRequest::checked).toList();
		if (checked.getLast().speaker() != VoiceSpeaker.CUSTOMER) {
			throw new BusinessException(CANNOT_CONTINUE);
		}
		return checked;
	}

	VoiceDraft draftOrEmpty() {
		return draft == null ? VoiceDraft.EMPTY : draft;
	}

	private static VoiceMessage checked(VoiceMessage message) {
		String text = message == null ? null : TextInput.normalize(message.text());
		if (text == null || text.isEmpty() || message.speaker() == null) {
			throw new BusinessException(CANNOT_CONTINUE);
		}
		int length = TextInput.length(text);
		if (message.speaker() == VoiceSpeaker.CUSTOMER && length > MAX_CUSTOMER_LENGTH) {
			throw new BusinessException(TOO_LONG_SPEECH);
		}
		if (message.speaker() == VoiceSpeaker.SYSTEM && length > MAX_SYSTEM_LENGTH) {
			throw new BusinessException(CANNOT_CONTINUE);
		}
		return new VoiceMessage(message.speaker(), text);
	}

}
