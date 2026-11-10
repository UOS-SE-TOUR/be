package com.mwt;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.swagger.v3.oas.models.responses.ApiResponses;
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
	 * 실패 응답(4xx·5xx)의 본문을 ErrorResponse({"message"})로 적는다. Controller에는 상태 코드와 설명만 적고, 본문 모양은
	 * 여기서 한 번에 붙인다. springdoc은 본문을 적지 않은 실패 응답에 성공 응답의 반환 타입을 채우므로, 본문이 없거나 성공
	 * 응답과 같은 것만 바꾼다 — @ApiResponse에 본문(예시 등)을 직접 적은 실패 응답은 그대로 둔다.
	 */
	@Bean
	GlobalOpenApiCustomizer errorResponseCustomizer() {
		return openApi -> {
			// 다른 스키마와 같은 판(3.1)으로 만들어야 "type": "object"가 붙는다
			ModelConverters.getInstance(openApi.getSpecVersion() == SpecVersion.V31).read(ErrorResponse.class)
				.forEach(openApi.getComponents()::addSchemas);
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
				ApiResponses responses = operation.getResponses();
				Content success = responses.entrySet().stream()
					.filter(entry -> entry.getKey().startsWith("2"))
					.map(entry -> entry.getValue().getContent())
					.filter(Objects::nonNull)
					.findFirst()
					.orElse(null);
				responses.forEach((code, response) -> {
					boolean error = code.startsWith("4") || code.startsWith("5");
					if (error && (response.getContent() == null || response.getContent().equals(success))) {
						response.setContent(errorContent());
					}
				});
			}));
		};
	}

	/**
	 * null일 수 있는 열거형 칸(@Schema(nullable = true))을 3.1 꼴인 anyOf [열거형, null]로 바꾼다.
	 * springdoc은 그런 칸을 {"$ref": 열거형, "type": "null"}로 내는데, 이 꼴은 "열거형이면서 null"이라 어떤 값도 맞지 않는다.
	 * 스키마의 칸과 목록 항목(안쪽까지), 요청 파라미터를 본다. 놓친 자리가 있으면 ApiDocsTest가 잡는다.
	 */
	@Bean
	GlobalOpenApiCustomizer nullableRefCustomizer() {
		return openApi -> {
			openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::fixNullableRefs);
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
				if (operation.getParameters() != null) {
					operation.getParameters().forEach(parameter -> fixNullableRefs(parameter.getSchema()));
				}
			}));
		};
	}

	private static Content errorContent() {
		return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE)));
	}

	/** 그 자리를 고친 뒤 안쪽(칸·목록 항목)으로 내려간다. 원래 스키마 객체를 고쳐 설명·예시 같은 다른 값은 남긴다. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void fixNullableRefs(Schema schema) {
		if (schema == null) {
			return;
		}
		if (schema.get$ref() != null && schema.getTypes() != null && schema.getTypes().contains("null")) {
			String ref = schema.get$ref();
			schema.set$ref(null);
			schema.setType(null);
			schema.setTypes(null);
			schema.setAnyOf(List.of(new JsonSchema().$ref(ref), new JsonSchema().types(Set.of("null"))));
		}
		Map<String, Schema> properties = schema.getProperties();
		if (properties != null) {
			properties.values().forEach(OpenApiConfig::fixNullableRefs);
		}
		fixNullableRefs(schema.getItems());
	}

}
