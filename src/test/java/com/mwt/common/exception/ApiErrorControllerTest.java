package com.mwt.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;

/**
 * Controller 밖에서 난 오류가 /error로 넘어왔을 때도 본문이 {"message": "..."}인지 본다.
 * 서블릿 컨테이너가 /error로 넘길 때 붙이는 상태 코드 속성을 직접 넣어 흉내 낸다.
 */
class ApiErrorControllerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ApiErrorController())
		.defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
		.build();

	@Test
	void 넘어온_상태_코드와_그_일반_문구() throws Exception {
		mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"message\": \"로그인이 필요합니다.\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 서버_오류는_500과_일반_문구() throws Exception {
		mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("{\"message\": \"서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.\"}",
					JsonCompareMode.STRICT));
	}

	@Test
	void 필터가_던진_업무_규칙_위반은_그_상태와_문구() throws Exception {
		BusinessException business = new BusinessException(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다.");
		mockMvc.perform(get("/error")
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new ServletException(business)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"message\": \"로그인이 만료되었습니다.\"}", JsonCompareMode.STRICT));
	}

	@Test
	void 다른_예외가_일부러_감싼_업무_규칙_위반은_500() throws Exception {
		BusinessException business = new BusinessException(HttpStatus.NOT_FOUND, "상품이 없습니다.");
		mockMvc.perform(get("/error")
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("설정 오류", business)))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("{\"message\": \"서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.\"}",
					JsonCompareMode.STRICT));
	}

	@Test
	void 오류_없이_바로_부르면_404() throws Exception {
		mockMvc.perform(get("/error"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"요청한 주소를 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

}
