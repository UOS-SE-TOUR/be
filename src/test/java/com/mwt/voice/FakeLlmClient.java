package com.mwt.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 LlmClient. OpenAI를 부르지 않고 정해 둔 답을 돌려주거나 정해 둔 예외를 던지며, 받은 글자를 남긴다.
 */
class FakeLlmClient implements LlmClient {

	private final TourSelection answer;
	private final RuntimeException failure;
	private final List<String> texts = new ArrayList<>();

	private FakeLlmClient(TourSelection answer, RuntimeException failure) {
		this.answer = answer;
		this.failure = failure;
	}

	static FakeLlmClient answering(TourSelection answer) {
		return new FakeLlmClient(answer, null);
	}

	static FakeLlmClient failing(RuntimeException failure) {
		return new FakeLlmClient(null, failure);
	}

	@Override
	public TourSelection extractSelection(String text) {
		texts.add(text);
		if (failure != null) {
			throw failure;
		}
		return answer;
	}

	List<String> texts() {
		return List.copyOf(texts);
	}

}
