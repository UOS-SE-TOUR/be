package com.mwt.notification;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 문자를 보내지 않고 보낼 내용을 로그로만 남긴다. NCP SENS 키가 없을 때(테스트, 시연 연습) 쓴다.
 * 받는 번호는 SensSmsSender와 같은 규칙(PhoneNumbers)으로 정리해 남긴다.
 */
@Slf4j
class FakeSmsSender implements SmsSender {

	@Override
	public void sendSms(List<String> phoneNumbers, String message) {
		try {
			PhoneNumbers.Recipients recipients = PhoneNumbers.classify(phoneNumbers);
			if (!recipients.rejected().isEmpty()) {
				log.warn("[가짜 문자] 휴대폰 번호 꼴이 아닌 번호는 빼고 보냅니다: {}", recipients.rejected());
			}
			log.info("[가짜 문자] 받는 사람={}, 내용={}", recipients.mobile(), message);
		}
		catch (RuntimeException e) {
			log.error("[가짜 문자] 로그를 남기지 못했습니다: phoneNumbers={}", phoneNumbers, e);
		}
	}

}
