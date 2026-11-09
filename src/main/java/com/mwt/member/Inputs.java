package com.mwt.member;

final class Inputs {

	private Inputs() {
	}

	static String strip(String value) {
		return value == null ? null : value.strip();
	}

}
