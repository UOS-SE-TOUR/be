package com.mwt.notification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 받는 번호 정리. 숫자만 남기고, 국가 번호(+82)로 적은 번호는 국내 번호로 바꾸고(normalize), 같은 번호는 한 번만 둔다.
 * 휴대폰 번호 꼴이 아닌 번호는 따로 모은다 — 잘못된 번호 하나가 같은 요청의 다른 사람까지 실패(400)시키지 않게.
 * SensSmsSender와 FakeSmsSender가 같이 써서 시연 연습과 실제 발송의 받는 사람이 같게 한다.
 */
final class PhoneNumbers {

	/** 010은 11자리, 011·016·017·018·019는 10·11자리. */
	private static final Pattern MOBILE_NUMBER = Pattern.compile("010[0-9]{8}|01[16789][0-9]{7,8}");

	private PhoneNumbers() {
	}

	static Recipients classify(List<String> phoneNumbers) {
		Map<Boolean, List<String>> numbers = phoneNumbers.stream()
				.filter(Objects::nonNull)
				.map(PhoneNumbers::normalize)
				.distinct()
				.collect(Collectors.partitioningBy(number -> MOBILE_NUMBER.matcher(number).matches()));
		return new Recipients(numbers.get(true), numbers.get(false));
	}

	static String digitsOnly(String phoneNumber) {
		return phoneNumber.replaceAll("[^0-9]", "");
	}

	/** 숫자만 남기고, 국가 번호 82로 시작하면(국내 번호는 0으로 시작한다) 0으로 시작하는 국내 번호로 바꾼다. */
	static String normalize(String phoneNumber) {
		String digits = digitsOnly(phoneNumber);
		if (!digits.startsWith("82")) {
			return digits;
		}
		String national = digits.substring(2);
		return national.startsWith("0") ? national : "0" + national;   // +82 010-…처럼 0을 남겨 적은 꼴도 받는다
	}

	/** mobile은 보낼 번호, rejected는 휴대폰 번호 꼴이 아니어서 뺀 번호. */
	record Recipients(List<String> mobile, List<String> rejected) {
	}

}
