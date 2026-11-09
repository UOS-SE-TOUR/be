package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.tour.Theme;

/**
 * 창고 물품 API(GET·POST /api/v1/staff/items, 설계산출물 5.9). 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WarehouseControllerTest {

	private static final String URL = "/api/v1/staff/items";
	private static final String NOT_READABLE = "요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ItemRepository itemRepository;

	@AfterEach
	void deleteItems() {
		itemRepository.deleteAll();
	}

	@Test
	void 물품이_없으면_빈_목록() throws Exception {
		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

	@Test
	void 새_물품을_추가하면_200과_추가한_물품을_돌려준다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\" 생수 \"", "\"GOLF\"", "10")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("생수"))
			.andExpect(jsonPath("$.theme").value("GOLF"))
			.andExpect(jsonPath("$.quantity").value(10));

		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getTheme, Item::getQuantity)
			.containsExactly(tuple("생수", Theme.GOLF, 10));
	}

	@Test
	void 같은_이름의_물품을_추가하면_수량만_더한_물품을_돌려준다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "10")))
			.andExpect(status().isOk());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "5")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("생수"))
			.andExpect(jsonPath("$.theme").value("GOLF"))
			.andExpect(jsonPath("$.quantity").value(15));

		assertThat(itemRepository.count()).isEqualTo(1);
	}

	@Test
	void 있는_물품에_다른_테마를_보내면_400이고_수량은_그대로다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "10")))
			.andExpect(status().isOk());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"TREKKING\"", "5")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json(
					"{\"message\": \"같은 이름의 물품이 이미 골프 챌린지 투어 테마로 있습니다. 테마를 확인해 주세요.\"}",
					JsonCompareMode.STRICT));

		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(10);
	}

	@Test
	void 있는_물품에_더할_때는_테마를_보내지_않아도_된다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "10")))
			.andExpect(status().isOk());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"itemName": "생수", "quantity": 5}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("GOLF"))
			.andExpect(jsonPath("$.quantity").value(15));
	}

	@Test
	void 새_물품인데_테마를_보내지_않으면_400() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"itemName": "생수", "quantity": 5}"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"테마를 골라 주세요.\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	@Test
	void 목록은_이름_순으로_이름_테마_현재_수량을_준다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"스카프\"", "\"TREKKING\"", "12")))
			.andExpect(status().isOk());
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"골프공\"", "\"GOLF\"", "30")))
			.andExpect(status().isOk());
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"골프공\"", "\"GOLF\"", "6")))
			.andExpect(status().isOk());

		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").isNumber())
			.andExpect(jsonPath("$[0].name").value("골프공"))
			.andExpect(jsonPath("$[0].theme").value("GOLF"))
			.andExpect(jsonPath("$[0].quantity").value(36))
			.andExpect(jsonPath("$[1].name").value("스카프"))
			.andExpect(jsonPath("$[1].theme").value("TREKKING"))
			.andExpect(jsonPath("$[1].quantity").value(12));
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("rejectedRequests")
	void 규칙에_어긋나면_400과_이유를_돌려주고_저장하지_않는다(String requestBody, String message) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	static Stream<Arguments> rejectedRequests() {
		return Stream.of(
				Arguments.of(body("\"생수\"", "\"GOLF\"", "0"), "수량은 1개 이상이어야 합니다."),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "-1"), "수량은 1개 이상이어야 합니다."),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "null"), "수량을 입력해 주세요."),
				Arguments.of(body("\"생수\"", "null", "10"), "테마를 골라 주세요."),
				Arguments.of(body("null", "\"GOLF\"", "10"), "물품 이름을 입력해 주세요."),
				Arguments.of(body("\"  \"", "\"GOLF\"", "10"), "물품 이름을 입력해 주세요."),
				Arguments.of(body("\"" + "가".repeat(101) + "\"", "\"GOLF\"", "10"), "물품 이름은 100자까지 쓸 수 있습니다."));
	}

	@Test
	void 있는_물품에_0을_더하려_하면_400이고_수량은_그대로다() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "10")))
			.andExpect(status().isOk());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", "0")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"수량은 1개 이상이어야 합니다.\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(10);
	}

	@ParameterizedTest
	@ValueSource(strings = {"2.5", "1.0", "2147483648", "\"많이\""})
	void 수량이_정수가_아니면_잘라_받지_않고_400(String quantity) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", "\"GOLF\"", quantity)))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + NOT_READABLE + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "\"2\"", "\"SKIING\""})
	void 없는_테마나_숫자_테마는_400(String theme) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"생수\"", theme, "10")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + NOT_READABLE + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	/** 값 자리에 JSON 조각(따옴표 포함)을 그대로 넣는다. */
	private static String body(String itemName, String theme, String quantity) {
		return """
				{"itemName": %s, "theme": %s, "quantity": %s}""".formatted(itemName, theme, quantity);
	}

}
