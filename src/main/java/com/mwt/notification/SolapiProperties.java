package com.mwt.notification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 솔라피 설정. application.yml이 환경변수 SOLAPI_API_KEY·SOLAPI_API_SECRET·SOLAPI_SENDER_NUMBER를 넣는다.
 */
@ConfigurationProperties("solapi")
record SolapiProperties(String apiKey, String apiSecret, String senderNumber) {

	/** .env 값 끝에 붙은 공백이 키에 섞이면 서명이 틀려 발송마다 실패하므로 앞뒤 공백을 뗀다. */
	SolapiProperties {
		apiKey = strip(apiKey);
		apiSecret = strip(apiSecret);
		senderNumber = strip(senderNumber);
	}

	boolean isEmpty() {
		return isBlank(apiKey) && isBlank(apiSecret) && isBlank(senderNumber);
	}

	/** 비어 있는 값의 환경변수 이름. */
	List<String> missingNames() {
		List<String> missing = new ArrayList<>();
		if (isBlank(apiKey)) {
			missing.add("SOLAPI_API_KEY");
		}
		if (isBlank(apiSecret)) {
			missing.add("SOLAPI_API_SECRET");
		}
		if (isBlank(senderNumber) || PhoneNumbers.digitsOnly(senderNumber).isEmpty()) {   // 보낼 때 숫자만 남긴다
			missing.add("SOLAPI_SENDER_NUMBER");
		}
		return missing;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String strip(String value) {
		return value == null ? null : value.strip();
	}

	@Override
	public String toString() {
		return "SolapiProperties[senderNumber=" + senderNumber + "]";
	}

}
