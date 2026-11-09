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
	void SENS_설정이_비어_있으면_가짜_발송기를_쓴다() {
		// application.yml이 환경변수가 없을 때 넣는 값(빈 문자열)과 같다
		runner.withPropertyValues("sens.access-key=", "sens.secret-key=", "sens.service-id=", "sens.sender-number=")
				.run(context -> assertThat(context).getBean(SmsSender.class).isInstanceOf(FakeSmsSender.class));
	}

	@Test
	void 환경변수_네_개를_모두_넣으면_SENS_발송기를_쓴다() {
		// application.yml을 읽어 SENS_* 이름이 sens.* 설정으로 들어가는지까지 본다(.env도 같은 길로 읽힌다)
		runner.withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("SENS_ACCESS_KEY=access", "SENS_SECRET_KEY=secret",
						"SENS_SERVICE_ID=ncp:sms:kr:123456789012:mwt", "SENS_SENDER_NUMBER=0212345678")
				.run(context -> assertThat(context).getBean(SmsSender.class).isInstanceOf(SensSmsSender.class));
	}

	@Test
	void SENS_설정이_일부만_있으면_서버가_뜨지_않는다() {
		runner.withPropertyValues("sens.access-key=access", "sens.secret-key=", "sens.service-id=",
				"sens.sender-number=0212345678")
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.hasMessageContaining("SENS_SECRET_KEY, SENS_SERVICE_ID"));
	}

	@Test
	void 발신_번호에_숫자가_없으면_서버가_뜨지_않는다() {
		runner.withPropertyValues("sens.access-key=access", "sens.secret-key=secret",
				"sens.service-id=ncp:sms:kr:123456789012:mwt", "sens.sender-number=미정")
				.run(context -> assertThat(context).hasFailed()
						.getFailure()
						.rootCause()
						.hasMessageContaining("SENS_SENDER_NUMBER"));
	}

}
