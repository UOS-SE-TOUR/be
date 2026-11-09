package com.mwt.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FakeSmsSenderTest {

	@Test
	void 솔라피_발송기와_같은_규칙으로_정리한_번호와_내용을_로그로_남긴다(CapturedOutput output) {
		new FakeSmsSender().sendSms(List.of("010-1234-5678", "+82 10-1111-2222", "+82 010-2222-3333", "02-123-4567"),
				"여행 상품이 확정되었습니다");

		assertThat(output).contains("받는 사람=[01012345678, 01011112222, 01022223333], 내용=여행 상품이 확정되었습니다")
				.contains("빼고 보냅니다: [021234567]");
	}

}
