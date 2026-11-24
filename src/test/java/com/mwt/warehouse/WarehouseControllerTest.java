package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.mwt.TestcontainersConfiguration;
import com.mwt.common.security.JwtTokenProvider;
import com.mwt.tour.Theme;

import jakarta.servlet.http.Cookie;

/**
 * 창고 물품 API(목록·등록·수량 더하기·1인당 개수 바꾸기, /api/v1/staff/items, 설계산출물 5.9, be #71).
 * 서버 전체를 MySQL(Testcontainers)에 붙여 띄운다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WarehouseControllerTest {

	private static final String URL = "/api/v1/staff/items";
	private static final String NOT_READABLE = "요청 본문을 읽을 수 없습니다. JSON 형식과 값을 확인해 주세요.";
	private static final String PER_PERSON_RANGE = "1인당 개수는 0개부터 100개까지입니다.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ItemRepository itemRepository;

	@Autowired
	ItemHistoryRepository itemHistoryRepository;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@AfterEach
	void deleteItems() {
		itemRepository.deleteAll();
		itemHistoryRepository.deleteAll();
	}

	@Test
	void 로그인하지_않았으면_401이고_고객이면_403이며_아무것도_바뀌지_않는다() throws Exception {
		long itemId = register("\"생수\"", "\"GOLF\"", "1", "10").getId();
		Cookie customer = new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "CUSTOMER"));
		List<MockHttpServletRequestBuilder> requests = List.of(
				get(URL),
				post(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"모자\"", "\"GOLF\"", "1", "10")),
				post(URL + "/" + itemId + "/quantity").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 5}"),
				post(URL + "/" + itemId + "/per-person").contentType(MediaType.APPLICATION_JSON).content("{\"perPerson\": 3}"));

		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(request).andExpect(status().isUnauthorized());
			mockMvc.perform(request.cookie(customer)).andExpect(status().isForbidden());
		}

		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getPerPerson, Item::getQuantity)
			.containsExactly(tuple("생수", 1, 10));
	}

	@Test
	void 물품이_없으면_빈_목록() throws Exception {
		mockMvc.perform(staffGet())
			.andExpect(status().isOk())
			.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

	@Test
	void 새_물품을_등록하면_200과_등록한_물품을_돌려준다() throws Exception {
		mockMvc.perform(staffPost(URL).content(body("\" 생수 \"", "\"GOLF\"", "2", "10")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("생수"))
			.andExpect(jsonPath("$.theme").value("GOLF"))
			.andExpect(jsonPath("$.perPerson").value(2))
			.andExpect(jsonPath("$.quantity").value(10));

		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getTheme, Item::getPerPerson, Item::getQuantity)
			.containsExactly(tuple("생수", Theme.GOLF, 2, 10));
	}

	@Test
	void 이름과_테마가_같은_물품을_다시_등록하면_400이고_수량을_더하지_않는다() throws Exception {
		register("\"Golf Ball\"", "\"GOLF\"", "1", "10");

		mockMvc.perform(staffPost(URL).content(body("\"golf ball\"", "\"GOLF\"", "1", "5")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"이미 있는 물품입니다. 목록에서 수량을 더해 주세요.\"}",
					JsonCompareMode.STRICT));

		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getQuantity)
			.containsExactly(tuple("Golf Ball", 10));
	}

	@Test
	void 같은_이름도_테마가_다르면_따로_등록된다() throws Exception {
		register("\"생수\"", "\"GOLF\"", "1", "10");

		mockMvc.perform(staffPost(URL).content(body("\"생수\"", "\"TREKKING\"", "0", "5")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("TREKKING"))
			.andExpect(jsonPath("$.perPerson").value(0))
			.andExpect(jsonPath("$.quantity").value(5));

		assertThat(itemRepository.findAll()).extracting(Item::getTheme, Item::getQuantity)
			.containsExactlyInAnyOrder(tuple(Theme.GOLF, 10), tuple(Theme.TREKKING, 5));
	}

	@Test
	void 목록은_이름_테마_순으로_1인당_개수와_현재_수량을_준다() throws Exception {
		register("\"스카프\"", "\"TREKKING\"", "1", "12");
		register("\"골프공\"", "\"TREKKING\"", "0", "2");
		long ball = register("\"골프공\"", "\"GOLF\"", "3", "30").getId();
		mockMvc.perform(staffPost(URL + "/" + ball + "/quantity").content("{\"quantity\": 6}"))
			.andExpect(status().isOk());

		mockMvc.perform(staffGet())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].id").value(ball))
			.andExpect(jsonPath("$[0].name").value("골프공"))
			.andExpect(jsonPath("$[0].theme").value("GOLF"))
			.andExpect(jsonPath("$[0].perPerson").value(3))
			.andExpect(jsonPath("$[0].quantity").value(36))
			.andExpect(jsonPath("$[1].name").value("골프공"))
			.andExpect(jsonPath("$[1].theme").value("TREKKING"))
			.andExpect(jsonPath("$[1].perPerson").value(0))
			.andExpect(jsonPath("$[1].quantity").value(2))
			.andExpect(jsonPath("$[2].name").value("스카프"))
			.andExpect(jsonPath("$[2].theme").value("TREKKING"))
			.andExpect(jsonPath("$[2].perPerson").value(1))
			.andExpect(jsonPath("$[2].quantity").value(12));
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("rejectedRegistrations")
	void 등록이_규칙에_어긋나면_400과_이유를_돌려주고_저장하지_않는다(String requestBody, String message) throws Exception {
		mockMvc.perform(staffPost(URL).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	static Stream<Arguments> rejectedRegistrations() {
		return Stream.of(
				Arguments.of(body("\"생수\"", "\"GOLF\"", "1", "0"), "수량은 1개 이상이어야 합니다."),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "1", "-1"), "수량은 1개 이상이어야 합니다."),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "1", "null"), "수량을 입력해 주세요."),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "-1", "10"), PER_PERSON_RANGE),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "101", "10"), PER_PERSON_RANGE),
				Arguments.of(body("\"생수\"", "\"GOLF\"", "null", "10"), "1인당 개수를 입력해 주세요."),
				Arguments.of(body("\"생수\"", "null", "1", "10"), "테마를 골라 주세요."),
				Arguments.of(body("null", "\"GOLF\"", "1", "10"), "물품 이름을 입력해 주세요."),
				Arguments.of(body("\"  \"", "\"GOLF\"", "1", "10"), "물품 이름을 입력해 주세요."),
				Arguments.of(body("\"" + "가".repeat(101) + "\"", "\"GOLF\"", "1", "10"), "물품 이름은 100자까지 쓸 수 있습니다."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2.5", "1.0", "2147483648", "\"많이\""})
	void 수량이나_1인당_개수가_정수가_아니면_잘라_받지_않고_400(String value) throws Exception {
		mockMvc.perform(staffPost(URL).content(body("\"생수\"", "\"GOLF\"", "1", value)))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + NOT_READABLE + "\"}", JsonCompareMode.STRICT));
		mockMvc.perform(staffPost(URL).content(body("\"생수\"", "\"GOLF\"", value, "10")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + NOT_READABLE + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "\"2\"", "\"SKIING\""})
	void 없는_테마나_숫자_테마는_400(String theme) throws Exception {
		mockMvc.perform(staffPost(URL).content(body("\"생수\"", theme, "1", "10")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + NOT_READABLE + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.count()).isZero();
	}

	@Test
	void 수량을_더하면_200과_더한_뒤의_물품을_돌려준다() throws Exception {
		long itemId = register("\"생수\"", "\"GOLF\"", "2", "10").getId();

		mockMvc.perform(staffPost(URL + "/" + itemId + "/quantity").content("{\"quantity\": 5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(itemId))
			.andExpect(jsonPath("$.name").value("생수"))
			.andExpect(jsonPath("$.perPerson").value(2))
			.andExpect(jsonPath("$.quantity").value(15));

		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(15);
	}

	@Test
	void 일인당_개수를_바꾸면_200과_바꾼_뒤의_물품을_돌려준다() throws Exception {
		long itemId = register("\"생수\"", "\"GOLF\"", "1", "10").getId();

		mockMvc.perform(staffPost(URL + "/" + itemId + "/per-person").content("{\"perPerson\": 100}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(itemId))
			.andExpect(jsonPath("$.perPerson").value(100))
			.andExpect(jsonPath("$.quantity").value(10));

		assertThat(itemRepository.findAll()).extracting(Item::getPerPerson).containsExactly(100);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("rejectedChanges")
	void 더하기와_바꾸기가_규칙에_어긋나면_400이고_그대로다(String path, String requestBody, String message) throws Exception {
		long itemId = register("\"생수\"", "\"GOLF\"", "1", "10").getId();

		mockMvc.perform(staffPost(URL + "/" + itemId + path).content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));

		assertThat(itemRepository.findAll()).extracting(Item::getPerPerson, Item::getQuantity)
			.containsExactly(tuple(1, 10));
	}

	static Stream<Arguments> rejectedChanges() {
		return Stream.of(
				Arguments.of("/quantity", "{\"quantity\": 0}", "수량은 1개 이상이어야 합니다."),
				Arguments.of("/quantity", "{\"quantity\": null}", "수량을 입력해 주세요."),
				Arguments.of("/quantity", "{\"quantity\": 2147483647}", "창고에 둘 수 있는 수량을 넘습니다."),
				Arguments.of("/quantity", "{\"quantity\": 1.5}", NOT_READABLE),
				Arguments.of("/per-person", "{\"perPerson\": 101}", PER_PERSON_RANGE),
				Arguments.of("/per-person", "{\"perPerson\": -1}", PER_PERSON_RANGE),
				Arguments.of("/per-person", "{}", "1인당 개수를 입력해 주세요."));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {"/quantity | {\"quantity\": 1}", "/per-person | {\"perPerson\": 1}"})
	void 없는_물품이면_404이고_물품_ID가_숫자가_아니면_400(String path, String requestBody) throws Exception {
		long itemId = register("\"생수\"", "\"GOLF\"", "1", "10").getId();

		mockMvc.perform(staffPost(URL + "/" + (itemId + 1000) + path).content(requestBody))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"message\": \"창고 물품을 찾을 수 없습니다.\"}", JsonCompareMode.STRICT));
		mockMvc.perform(staffPost(URL + "/abc" + path).content(requestBody))
			.andExpect(status().isBadRequest());

		assertThat(itemRepository.findAll()).extracting(Item::getPerPerson, Item::getQuantity)
			.containsExactly(tuple(1, 10));
	}

	/** 등록 API로 넣고 저장된 물품을 돌려준다. */
	private Item register(String itemName, String theme, String perPerson, String quantity) throws Exception {
		ResultActions result = mockMvc.perform(staffPost(URL).content(body(itemName, theme, perPerson, quantity)))
			.andExpect(status().isOk());
		Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
		return itemRepository.findById(id.longValue()).orElseThrow();
	}

	/** 직원으로 로그인한 요청. 주소별 권한 규칙은 common.security의 SecurityConfigTest가 따로 본다. */
	private MockHttpServletRequestBuilder staffPost(String url) {
		return post(url).contentType(MediaType.APPLICATION_JSON).cookie(staffCookie());
	}

	private MockHttpServletRequestBuilder staffGet() {
		return get(URL).cookie(staffCookie());
	}

	private Cookie staffCookie() {
		return new Cookie(JwtTokenProvider.COOKIE_NAME, jwtTokenProvider.createToken(1, "STAFF"));
	}

	/** 값 자리에 JSON 조각(따옴표 포함)을 그대로 넣는다. */
	private static String body(String itemName, String theme, String perPerson, String quantity) {
		return """
				{"itemName": %s, "theme": %s, "perPerson": %s, "quantity": %s}""".formatted(itemName, theme, perPerson,
				quantity);
	}

}
