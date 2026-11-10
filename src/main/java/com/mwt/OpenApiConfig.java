package com.mwt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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
 * 여러 곳에 같은 설명 문구는 common.docs의 ApiDescriptions에 있다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String ERROR_RESPONSE = "ErrorResponse";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("MWT Tour Server API").version("v1")
				.description("오류 응답은 모두 상태 코드와 {\"message\": \"오류 문구\"}입니다."))
			.servers(List.of(new Server().url("/")));
	}

	/**
	 * 실패 응답(4xx·5xx)의 본문 스키마를 모두 ErrorResponse({"message"})로 적는다. Controller에는 상태 코드와 설명만 적고,
	 * 본문 모양은 여기서 한 번에 붙인다. 서버의 실패 응답은 늘 이 모양이라(GlobalExceptionHandler·ApiErrorController)
	 * 스키마는 무조건 바꾼다 — springdoc은 본문을 적지 않은 실패 응답에 성공 응답의 반환 타입을 채운다.
	 * 스키마 말고 @ApiResponse에 직접 적은 것(예시 등)은 그대로 둔다.
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
						response.setContent(errorContent(response.getContent()));
					}
				})));
		};
	}

	/**
	 * 새 Content를 만든다 — springdoc이 채운 Content·MediaType은 성공 응답과 같은 객체일 수 있어 그 자리에서 고치면 성공 응답까지
	 * 바뀐다. 원래 본문의 형식(media type)과 예시는 옮긴다.
	 */
	private static Content errorContent(Content original) {
		Content content = new Content();
		if (original == null || original.isEmpty()) {
			return content.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
					new MediaType().schema(errorSchema()));
		}
		original.forEach((type, mediaType) -> {
			MediaType copy = new MediaType().schema(errorSchema()).examples(mediaType.getExamples())
				.encoding(mediaType.getEncoding()).extensions(mediaType.getExtensions());
			if (mediaType.getExampleSetFlag()) {
				copy.setExample(mediaType.getExample());
			}
			content.addMediaType(type, copy);
		});
		return content;
	}

	/**
	 * null일 수 있는 열거형 칸(@Schema(nullable = true))을 3.1 꼴인 anyOf [열거형, null]로 바꾼다.
	 * springdoc은 그런 칸을 {"$ref": 열거형, "type": "null"}로 내는데, 이 꼴은 "열거형이면서 null"이라 어떤 값도 맞지 않는다.
	 * 스키마의 안쪽(칸·목록 항목·Map 값·allOf/anyOf/oneOf)과 요청 파라미터·요청과 응답 본문을 본다. 놓친 자리가 있으면
	 * ApiDocsTest가 잡는다.
	 */
	@Bean
	GlobalOpenApiCustomizer nullableRefCustomizer() {
		return openApi -> {
			openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::fixNullableRefs);
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
				if (operation.getParameters() != null) {
					operation.getParameters().forEach(parameter -> fixNullableRefs(parameter.getSchema()));
				}
				if (operation.getRequestBody() != null) {
					fixNullableRefs(operation.getRequestBody().getContent());
				}
				operation.getResponses().values().forEach(response -> fixNullableRefs(response.getContent()));
			}));
		};
	}

	private static Schema<?> errorSchema() {
		return new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE);
	}

	private static void fixNullableRefs(Content content) {
		if (content != null) {
			content.values().forEach(mediaType -> fixNullableRefs(mediaType.getSchema()));
		}
	}

	/** 그 자리를 고친 뒤 안쪽으로 내려간다. 원래 스키마 객체를 고쳐 설명·예시 같은 다른 값은 남긴다. */
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
			// 뒤에 도는 설정이 더 넣을 수 있게 바꿀 수 있는 목록으로 둔다
			schema.setAnyOf(new ArrayList<>(List.of(new JsonSchema().$ref(ref),
					new JsonSchema().types(new LinkedHashSet<>(Set.of("null"))))));
		}
		Map<String, Schema> properties = schema.getProperties();
		if (properties != null) {
			properties.values().forEach(OpenApiConfig::fixNullableRefs);
		}
		fixNullableRefs(schema.getItems());
		if (schema.getAdditionalProperties() instanceof Schema valueSchema) {
			fixNullableRefs(valueSchema);
		}
		Stream.of(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())
			.filter(Objects::nonNull)
			.forEach(schemas -> ((List<Schema>) schemas).forEach(OpenApiConfig::fixNullableRefs));
	}

}
