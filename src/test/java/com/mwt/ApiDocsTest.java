package com.mwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 서버 전체를 띄워 API 문서(Swagger) 주소와, 없는 API 주소의 오류 응답을 본다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void Swagger_UI_주소는_화면으로_넘긴다() throws Exception {
		mockMvc.perform(get("/api/swagger-ui.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/api/swagger-ui/index.html"));
		mockMvc.perform(get("/api/swagger-ui/index.html"))
			.andExpect(status().isOk());
	}

	@Test
	void API_문서를_JSON으로_준다() throws Exception {
		mockMvc.perform(get("/api/docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("MWT Tour Server API"))
			.andExpect(jsonPath("$.servers[0].url").value("/"));
	}

	@Test
	void 없는_API_주소는_404와_문구() throws Exception {
		mockMvc.perform(get("/api/v1/nothing"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"요청한 주소를 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
	}

}
