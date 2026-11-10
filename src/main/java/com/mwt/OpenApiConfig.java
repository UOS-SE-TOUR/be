package com.mwt;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mwt.common.exception.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;

/**
 * API 문서(Swagger)의 제목과 서버 주소. 주소는 application.yml의 springdoc 절에 있다.
 * 서버 주소를 "/"로 두어 Swagger UI의 "Try it out"이 문서를 연 주소로 요청을 보내게 한다
 * (기본값은 서버가 받은 요청 주소라 프록시 뒤에서는 안쪽 주소가 적힌다).
 * Controller의 @ApiResponse가 여러 API에서 같은 실패를 설명할 때는 아래 상수를 쓴다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String UNAUTHORIZED = "로그인하지 않았거나 로그인이 만료됨";
	public static final String NOT_CUSTOMER = "고객 계정이 아님";
	public static final String NOT_STAFF = "직원 계정이 아님";
	public static final String TOUR_NOT_FOUND = "여행 상품이 없음";

	private static final String ERROR_RESPONSE = "ErrorResponse";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("MWT Tour Server API").version("v1")
				.description("오류 응답은 모두 상태 코드와 {\"message\": \"오류 문구\"}입니다."))
			.servers(List.of(new Server().url("/")));
	}

	/**
	 * 실패 응답(4xx·5xx)의 본문을 모두 ErrorResponse({"message"})로 적는다.
	 * Controller에는 상태 코드와 설명만 적고, 본문 모양은 여기서 한 번에 붙인다.
	 */
	@Bean
	OpenApiCustomizer errorResponseCustomizer() {
		return openApi -> {
			ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
			Content errorContent = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE)));
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
				operation.getResponses().forEach((code, response) -> {
					if (code.startsWith("4") || code.startsWith("5")) {
						response.setContent(errorContent);
					}
				})));
		};
	}

}
