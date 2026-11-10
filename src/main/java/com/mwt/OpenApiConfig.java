package com.mwt;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mwt.common.exception.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
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

	/** 여러 응답에 같은 칸 설명. 상품 이름의 설명은 이름을 만드는 TourSummary.TITLE_DESCRIPTION에 있다. */
	public static final String DURATION_DAYS = "여행 기간(일)";
	public static final String LEVEL_NAME = "투어 등급의 화면 이름(예: 그랜드)";

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
	 * Controller에는 상태 코드와 설명만 적고, 본문 모양은 여기서 한 번에 붙인다. 서버의 실패 응답은 늘 이 모양이라
	 * (GlobalExceptionHandler·ApiErrorController) springdoc이 채운 본문(성공 응답의 반환 타입)을 덮어쓴다.
	 * 실패 응답에 예시를 따로 달려면 이 덮어쓰기를 먼저 고친다.
	 */
	@Bean
	GlobalOpenApiCustomizer errorResponseCustomizer() {
		return openApi -> {
			// 다른 스키마와 같은 판(3.1)으로 만들어야 "type": "object"가 붙는다
			ModelConverters.getInstance(openApi.getSpecVersion() == SpecVersion.V31).read(ErrorResponse.class)
				.forEach(openApi.getComponents()::addSchemas);
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
				operation.getResponses().forEach((code, response) -> {
					if (code.startsWith("4") || code.startsWith("5")) {
						response.setContent(errorContent());
					}
				})));
		};
	}

	/**
	 * null일 수 있는 열거형 칸(@Schema(nullable = true))을 3.1 꼴인 anyOf [열거형, null]로 바꾼다.
	 * springdoc은 그런 칸을 {"$ref": 열거형, "type": "null"}로 내는데, 이 꼴은 "열거형이면서 null"이라 어떤 값도 맞지 않는다.
	 */
	@Bean
	GlobalOpenApiCustomizer nullableRefCustomizer() {
		return openApi -> openApi.getComponents().getSchemas().values().forEach(schema -> {
			@SuppressWarnings("rawtypes")
			Map<String, Schema> properties = schema.getProperties();
			if (properties != null) {
				properties.replaceAll((name, property) -> isNullableRef(property) ? nullableRef(property) : property);
			}
		});
	}

	private static Content errorContent() {
		return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE)));
	}

	private static boolean isNullableRef(Schema<?> property) {
		return property.get$ref() != null && property.getTypes() != null && property.getTypes().contains("null");
	}

	private static Schema<?> nullableRef(Schema<?> property) {
		return new JsonSchema()
			.anyOf(List.of(new JsonSchema().$ref(property.get$ref()), new JsonSchema().types(Set.of("null"))))
			.description(property.getDescription());
	}

}
