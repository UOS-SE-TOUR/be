package com.mwt;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * API 문서(Swagger)의 제목과 서버 주소. 주소는 application.yml의 springdoc 절에 있다.
 * 서버 주소를 "/"로 두어 Swagger UI의 "Try it out"이 문서를 연 주소로 요청을 보내게 한다
 * (기본값은 서버가 받은 요청 주소라 프록시 뒤에서는 안쪽 주소가 적힌다).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("MWT Tour Server API").version("v1")
				.description("오류 응답은 모두 상태 코드와 {\"message\": \"오류 문구\"}입니다."))
			.servers(List.of(new Server().url("/")));
	}

}
