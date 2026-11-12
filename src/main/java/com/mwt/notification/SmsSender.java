package com.mwt.notification;

import java.util.List;

/**
 * notification 모듈이 공개하는 문자 발송 인터페이스(설계산출물 4.6).
 * 솔라피 키가 있으면 SolapiSmsSender가, 없으면 보낸 내용을 로그로만 남기는 FakeSmsSender가 빈으로 등록된다.
 */
public interface SmsSender {

	/**
	 * 같은 문자를 여러 번호로 보낸다. 번호는 숫자만 남기고, 국가 번호(+82)로 적은 번호는 0으로 시작하는 국내 번호로 바꾸고,
	 * 같은 번호에는 한 번만 보낸다. 휴대폰 번호 꼴이 아닌 번호는 빼고 보낸다.
	 * 발송에 실패해도 예외를 던지지 않고 서버 로그에 남긴다 — 신청 트랜잭션이 커밋된 뒤에 부르므로,
	 * 여기서 던지면 신청은 저장됐는데 응답만 실패한다(설계산출물 5.4.1).
	 */
	void sendSms(List<String> phoneNumbers, String message);

	/**
	 * sendSms가 빼지 않고 보내는 번호(휴대폰 번호 꼴)인지. 회원가입이 연락처를 받을 때 같은 규칙으로 확인한다.
	 * 숫자가 아닌 글자는 보지 않고(어떤 글자를 받을지는 부르는 쪽이 정한다), 국가 번호(+82)로 적은 번호는 국내 번호로 바꿔서 본다.
	 */
	static boolean isMobileNumber(String phoneNumber) {
		return phoneNumber != null && PhoneNumbers.isMobile(phoneNumber);
	}

}
