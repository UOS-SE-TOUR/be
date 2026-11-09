package com.mwt.notification;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 문자를 보내지 않고 보낼 내용을 로그로만 남긴다. NCP SENS 키가 없을 때(테스트, 시연 연습) 쓴다.
 */
@Slf4j
class FakeSmsSender implements SmsSender {

	@Override
	public void sendSms(List<String> phoneNumbers, String message) {
		log.info("[가짜 문자] 받는 사람={}, 내용={}", phoneNumbers, message);
	}

}
