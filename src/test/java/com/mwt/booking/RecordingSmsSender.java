package com.mwt.booking;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mwt.notification.SmsSender;

/**
 * 보내지 않고 받는 번호와 내용을 기록하는 테스트용 발송기. 신청 흐름 테스트가 @TestBean으로 SmsSender 자리에 넣는다.
 * 동시 신청 테스트에서 여러 스레드가 함께 부르므로 스레드에 안전한 목록에 담는다.
 */
class RecordingSmsSender implements SmsSender {

	record Sent(List<String> phoneNumbers, String message) {
	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();

	@Override
	public void sendSms(List<String> phoneNumbers, String message) {
		sent.add(new Sent(List.copyOf(phoneNumbers), message));
	}

	List<Sent> sent() {
		return List.copyOf(sent);
	}

	void clear() {
		sent.clear();
	}

}
