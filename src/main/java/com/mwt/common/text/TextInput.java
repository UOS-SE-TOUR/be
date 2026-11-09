package com.mwt.common.text;

import java.text.Normalizer;

import org.jspecify.annotations.Nullable;

import com.mwt.common.exception.BusinessException;

/**
 * 사람이 입력한 이름·아이디 같은 글자를 다듬는다. 모듈마다 따로 다듬으면 같은 이름이 모듈마다 달라지므로 모두 이것을 부른다.
 *
 * <ul>
 * <li>NFC로 모은다. macOS에서 붙여 넣은 글은 한글이 자모로 풀린 꼴(NFD)일 수 있어, 모으지 않으면 눈으로 같은 이름이 다른 글자가 된다.</li>
 * <li>앞뒤 공백을 뗀다. {@link String#strip()}이 떼지 않는 줄 바꿈 없는 공백(U+00A0)과 폭 없는 공백(U+200B) 같은 보이지 않는
 * 글자도 뗀다.</li>
 * <li>글자 수는 DB(utf8mb4 VARCHAR)처럼 유니코드 코드포인트로 센다. 이모지 하나도 한 글자다.</li>
 * </ul>
 *
 * 비밀번호는 공백도 비밀번호의 일부이므로 이것으로 다듬지 않는다.
 */
public final class TextInput {

	private TextInput() {
	}

	/** NFC로 모으고 앞뒤 공백(보이지 않는 공백 포함)을 뗀다. null은 null로 둔다. */
	public static @Nullable String normalize(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String composed = Normalizer.normalize(value, Normalizer.Form.NFC);
		int start = 0;
		int end = composed.length();
		while (start < end && isBlank(composed.codePointAt(start))) {
			start += Character.charCount(composed.codePointAt(start));
		}
		while (end > start && isBlank(composed.codePointBefore(end))) {
			end -= Character.charCount(composed.codePointBefore(end));
		}
		return composed.substring(start, end);
	}

	/**
	 * 꼭 입력해야 하는 글자를 다듬어 돌려준다. 다듬은 뒤 비었거나 maxLength자를 넘으면 label로 만든 문구로 거부한다
	 * (예: label이 "물품 이름"이면 "물품 이름을 입력해 주세요.", "물품 이름은 100자까지 쓸 수 있습니다.").
	 */
	public static String require(@Nullable String value, String label, int maxLength) {
		boolean finalConsonant = endsWithFinalConsonant(label);   // 잘못된 항목 이름은 입력이 맞아도 바로 드러나게 먼저 본다
		String normalized = normalize(value);
		if (normalized == null || normalized.isEmpty()) {
			throw new BusinessException(label + (finalConsonant ? "을" : "를") + " 입력해 주세요.");
		}
		if (length(normalized) > maxLength) {
			throw new BusinessException(label + (finalConsonant ? "은" : "는") + " " + maxLength + "자까지 쓸 수 있습니다.");
		}
		return normalized;
	}

	/** 글자 수. DB처럼 코드포인트로 센다. */
	public static int length(String value) {
		return value.codePointCount(0, value.length());
	}

	private static boolean isBlank(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || isInvisible(codePoint);
	}

	/**
	 * 공백 문자로 치지 않지만 화면에 보이지 않는 글자. 앞뒤에서만 떼므로 이모지 가운데의 결합자(U+200D)는 남는다.
	 * 이모지 끝의 모양 선택자(U+FE0F)와 깃발 이모지 끝의 태그 글자는 보이는 글자의 일부라 넣지 않는다.
	 */
	private static boolean isInvisible(int codePoint) {
		return switch (codePoint) {
			case 0x00AD,                                   // 보이지 않는 하이픈
					0x115F, 0x1160, 0x3164, 0xFFA0,        // 한글 채움 문자(빈 이름처럼 보이게 쓰인다)
					0x180E,                                // 몽골어 모음 구분자
					0x200B, 0x200C, 0x200D,                // 폭 없는 공백·비결합자·결합자
					0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
					0x2066, 0x2067, 0x2068, 0x2069,        // 글자 방향 표시·지정·격리
					0x2060, 0x2061, 0x2062, 0x2063, 0x2064, // 단어 결합자, 보이지 않는 수식 기호
					0xFEFF -> true;                        // BOM(폭 없는 줄 바꿈 없는 공백)
			default -> false;
		};
	}

	/** 마지막 글자에 받침이 있는지. 조사를 고를 수 없으니 label은 한글 음절로 끝나야 한다. */
	private static boolean endsWithFinalConsonant(String label) {
		char last = label.isEmpty() ? 0 : label.charAt(label.length() - 1);
		if (last < '가' || last > '힣') {
			throw new IllegalArgumentException("항목 이름은 한글 음절로 끝나야 조사를 고를 수 있다: " + label);
		}
		return (last - '가') % 28 != 0;
	}

}
