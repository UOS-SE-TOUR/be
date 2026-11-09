package com.mwt.member;

/**
 * 입력 값 다듬기.
 */
final class Inputs {

	private Inputs() {
	}

	static String strip(String value) {
		return value == null ? null : value.strip();
	}

}
