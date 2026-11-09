package com.mwt.common.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 오류 응답 본문이 상태 코드와 상관없이 {"message": "..."} 하나뿐인지 본다.
 * 스프링 컨텍스트 없이 테스트용 Controller와 GlobalExceptionHandler만 붙인다.
 */
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
		.build();

	@Test
	void 업무_규칙_위반은_400과_던진_문구() throws Exception {
		mockMvc.perform(get("/test/business"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(onlyMessage("남은 자리보다 인원이 많습니다."));
	}

	@Test
	void 업무_규칙_위반에_상태를_주면_그_상태로() throws Exception {
		mockMvc.perform(get("/test/conflict"))
			.andExpect(status().isConflict())
			.andExpect(onlyMessage("이미 마감된 상품입니다."));
	}

	@Test
	void 업무_규칙_위반은_5xx를_받지_않는다() {
		assertThatThrownBy(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 잘못"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 요청_본문_검증_실패는_400과_필드_이름_순_첫_문구() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\": \"\", \"loginId\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("아이디를 입력해 주세요."));
	}

	@Test
	void JSON_형식_오류는_400() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요."));
	}

	@Test
	void 필수_요청_값이_없으면_400() throws Exception {
		mockMvc.perform(get("/test/param"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("필수 요청 값이 없습니다: count"));
	}

	@Test
	void 요청_값의_형식이_틀리면_400() throws Exception {
		mockMvc.perform(get("/test/param").param("count", "two"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("요청 값의 형식이 올바르지 않습니다: count"));
	}

	@Test
	void 요청_값_검증_실패는_400과_검증_문구() throws Exception {
		mockMvc.perform(get("/test/param").param("count", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("인원은 1 이상이어야 합니다."));
	}

	@Test
	void 쿼리_객체_바인딩_실패는_400과_화면용_문구() throws Exception {
		mockMvc.perform(get("/test/search").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("요청 값의 형식이 올바르지 않습니다: page"));
	}

	@Test
	void Validated_방식의_검증_실패도_400과_검증_문구() throws Exception {
		mockMvc.perform(get("/test/constraint"))
			.andExpect(status().isBadRequest())
			.andExpect(onlyMessage("아이디를 입력해 주세요."));
	}

	@Test
	void 스프링이_정한_5xx도_일반_문구만() throws Exception {
		mockMvc.perform(get("/test/conversion"))
			.andExpect(status().isInternalServerError())
			.andExpect(onlyMessage("서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요."));
	}

	@Test
	void 없는_주소는_404() throws Exception {
		mockMvc.perform(get("/test/nothing"))
			.andExpect(status().isNotFound())
			.andExpect(onlyMessage("요청한 주소를 찾을 수 없습니다."));
	}

	@Test
	void 지원하지_않는_메소드는_405() throws Exception {
		mockMvc.perform(delete("/test/business"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(onlyMessage("지원하지 않는 요청 방식입니다."));
	}

	@Test
	void 예상하지_못한_오류는_500과_일반_문구만() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(onlyMessage("서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요."))
			.andExpect(content().string(not(containsString("DB 비밀번호"))));
	}

	private static ResultMatcher onlyMessage(String message) {
		return content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT);
	}

	@RestController
	@RequestMapping("/test")
	static class TestController {

		@GetMapping("/business")
		void business() {
			throw new BusinessException("남은 자리보다 인원이 많습니다.");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new BusinessException(HttpStatus.CONFLICT, "이미 마감된 상품입니다.");
		}

		@PostMapping("/body")
		void body(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/param")
		void param(@RequestParam @Min(value = 1, message = "인원은 1 이상이어야 합니다.") int count) {
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("DB 비밀번호가 틀렸다");
		}

		@GetMapping("/search")
		void search(SearchRequest request) {
		}

		@GetMapping("/constraint")
		void constraint() {
			try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
				throw new ConstraintViolationException(factory.getValidator().validate(new TestRequest("", "비밀번호")));
			}
		}

		@GetMapping("/conversion")
		void conversion() {
			throw new ConversionNotSupportedException("값", String.class, null);
		}

	}

	record SearchRequest(int page) {
	}

	record TestRequest(
			@NotBlank(message = "아이디를 입력해 주세요.") String loginId,
			@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
	}

}
