package com.mwt.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 회원가입이 쓰는 휴대폰 번호 판정이 발송할 때 받는 사람을 고르는 규칙(PhoneNumbers.classify)과 같은지 본다.
 */
class SmsSenderTest {

	@ParameterizedTest
	@ValueSource(strings = { "010-1234-5678", "01012345678", "+82 10-1234-5678", "82-010-1234-5678", "016-123-4567" })
	void 보내는_번호는_휴대폰_번호다(String phoneNumber) {
		assertThat(SmsSender.isMobileNumber(phoneNumber)).isTrue();
		assertThat(PhoneNumbers.classify(List.of(phoneNumber)).mobile()).hasSize(1);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "02-123-4567", "010-1234-567", "0101234567", "abc" })
	void 빼고_보내는_번호는_휴대폰_번호가_아니다(String phoneNumber) {
		assertThat(SmsSender.isMobileNumber(phoneNumber)).isFalse();
		if (phoneNumber != null) {
			assertThat(PhoneNumbers.classify(List.of(phoneNumber)).mobile()).isEmpty();
		}
	}

}
