package com.mwt.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.mwt.ClockConfig;

class SmsSenderConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(SmsSenderConfig.class, ClockConfig.class);

	@Test
	void 솔라피_설정이_비어_있으면_가짜_발송기를_쓴다() {
		// application.yml이 환경변수가 없을 때 넣는 값(빈 문자열)과 같다
		runner.withPropertyValues("solapi.api-key=", "solapi.api-secret=", "solapi.sender-number=")
				.run(context -> assertThat(context).getBean(SmsSender.class).isInstanceOf(FakeSmsSender.class));
	}

	@Test
	void 환경변수_세_개를_모두_넣으면_솔라피_발송기를_쓴다() {
		// application.yml을 읽어 SOLAPI_* 이름이 solapi.* 설정으로 들어가는지까지 본다(.env도 같은 길로 읽힌다)
		runner.withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("SOLAPI_API_KEY=key", "SOLAPI_API_SECRET=secret", "SOLAPI_SENDER_NUMBER=01012345678")
				.run(context -> assertThat(context).getBean(SmsSender.class).isInstanceOf(SolapiSmsSender.class));
	}

	@Test
	void 솔라피_설정이_일부만_있으면_서버가_뜨지_않는다() {
		runner.withPropertyValues("solapi.api-key=key", "solapi.api-secret=", "solapi.sender-number=")
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.hasMessageContaining("SOLAPI_API_SECRET, SOLAPI_SENDER_NUMBER"));
	}

	@Test
	void 설정_값의_앞뒤_공백은_뗀다() {
		assertThat(new SolapiProperties(" key ", "secret\t", "010-1234-5678 "))
				.extracting(SolapiProperties::apiKey, SolapiProperties::apiSecret, SolapiProperties::senderNumber)
				.containsExactly("key", "secret", "010-1234-5678");
	}

	@Test
	void 설정을_문자열로_남길_때_키를_드러내지_않는다() {
		assertThat(new SolapiProperties("visible-key", "visible-secret", "01012345678").toString())
				.doesNotContain("visible-key", "visible-secret")
				.contains("01012345678");
	}

	@Test
	void 발신_번호에_숫자가_없으면_서버가_뜨지_않는다() {
		runner.withPropertyValues("solapi.api-key=key", "solapi.api-secret=secret", "solapi.sender-number=미정")
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.hasMessageContaining("SOLAPI_SENDER_NUMBER"));
		// 발신 번호만 적어 둔 것도 일부만 넣은 것이다(가짜 발송기로 조용히 넘어가지 않는다)
		runner.withPropertyValues("solapi.api-key=", "solapi.api-secret=", "solapi.sender-number=미정")
				.run(context -> assertThat(context).hasFailed());
	}

}
