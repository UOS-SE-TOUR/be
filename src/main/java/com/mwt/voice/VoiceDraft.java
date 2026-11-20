package com.mwt.voice;

import java.util.List;
import java.util.Objects;

import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 음성 대화에서 지금까지 고른 내용이다. 고르지 않은 칸은 null이고 옵션은 빈 목록이다.
 */
@Schema(description = "음성 대화에서 지금까지 고른 내용. 고르지 않은 칸은 null, 옵션은 빈 목록")
public record VoiceDraft(@Schema(description = "테마", nullable = true) Theme theme,
		@Schema(description = "상품 ID(상품 목록의 tourId)", nullable = true) Long tourId,
		@Schema(description = "투어 등급", nullable = true) TourLevel level,
		@Schema(description = "인원(명)", nullable = true) Integer headcount,
		@Schema(description = "고른 옵션의 ID(옵션 목록의 optionId). 없으면 빈 목록") List<Long> optionIds) {

	static final VoiceDraft EMPTY = new VoiceDraft(null, null, null, null, List.of());

	public VoiceDraft {
		optionIds = optionIds == null ? List.of() : optionIds.stream().filter(Objects::nonNull).toList();
	}

}
