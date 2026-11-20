package com.mwt.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 LlmClient. OpenAI를 부르지 않고 정해 둔 답을 돌려주거나 정해 둔 예외를 던지며, 받은 정보와 대화를 남긴다.
 */
class FakeLlmClient implements LlmClient {

	private final VoiceChatResponse answer;
	private final RuntimeException failure;
	private final List<VoiceContext> contexts = new ArrayList<>();
	private final List<List<VoiceMessage>> conversations = new ArrayList<>();

	private FakeLlmClient(VoiceChatResponse answer, RuntimeException failure) {
		this.answer = answer;
		this.failure = failure;
	}

	static FakeLlmClient answering(VoiceChatResponse answer) {
		return new FakeLlmClient(answer, null);
	}

	static FakeLlmClient failing(RuntimeException failure) {
		return new FakeLlmClient(null, failure);
	}

	@Override
	public VoiceChatResponse chat(VoiceContext context, List<VoiceMessage> messages) {
		contexts.add(context);
		conversations.add(List.copyOf(messages));
		if (failure != null) {
			throw failure;
		}
		return answer;
	}

	List<VoiceContext> contexts() {
		return List.copyOf(contexts);
	}

	List<List<VoiceMessage>> conversations() {
		return List.copyOf(conversations);
	}

}
