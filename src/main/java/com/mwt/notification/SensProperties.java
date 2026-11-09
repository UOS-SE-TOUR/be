package com.mwt.notification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NCP SENS 설정. application.yml이 환경변수 SENS_ACCESS_KEY·SENS_SECRET_KEY·SENS_SERVICE_ID·SENS_SENDER_NUMBER를 넣는다.
 */
@ConfigurationProperties("sens")
record SensProperties(String accessKey, String secretKey, String serviceId, String senderNumber) {

	boolean isEmpty() {
		return isBlank(accessKey) && isBlank(secretKey) && isBlank(serviceId) && isBlank(senderNumber);
	}

	/** 비어 있는 값의 환경변수 이름. */
	List<String> missingNames() {
		List<String> missing = new ArrayList<>();
		if (isBlank(accessKey)) {
			missing.add("SENS_ACCESS_KEY");
		}
		if (isBlank(secretKey)) {
			missing.add("SENS_SECRET_KEY");
		}
		if (isBlank(serviceId)) {
			missing.add("SENS_SERVICE_ID");
		}
		if (isBlank(senderNumber) || !senderNumber.matches(".*[0-9].*")) {   // 보낼 때 숫자만 남긴다
			missing.add("SENS_SENDER_NUMBER");
		}
		return missing;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@Override
	public String toString() {
		return "SensProperties[serviceId=" + serviceId + ", senderNumber=" + senderNumber + "]";
	}

}
