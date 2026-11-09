package com.mwt.notification;

import java.util.List;

/**
 * notification 모듈이 공개하는 문자 발송 인터페이스(설계산출물 v0.4 4.6).
 * NCP SENS 키가 있으면 SensSmsSender가, 없으면 보낸 내용을 로그로만 남기는 FakeSmsSender가 빈으로 등록된다.
 */
public interface SmsSender {

	/**
	 * 같은 문자를 여러 번호로 보낸다. 번호의 하이픈 등 숫자가 아닌 글자는 떼고, 휴대폰 번호 꼴이 아닌 번호는 빼고 보낸다.
	 * 발송에 실패해도 예외를 던지지 않고 서버 로그에 남긴다 — 신청 트랜잭션이 커밋된 뒤에 부르므로,
	 * 여기서 던지면 신청은 저장됐는데 응답만 실패한다(설계산출물 v0.4 5.4.1).
	 */
	void sendSms(List<String> phoneNumbers, String message);

}
