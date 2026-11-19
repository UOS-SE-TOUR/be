package com.mwt.warehouse;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.common.docs.ApiDescriptions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 창고 물품 조회, 등록, 수량 더하기, 1인당 개수 바꾸기 요청을 받는다(설계산출물 4.8, 5.9).
 * 1인당 개수 바꾸기도 POST다 — 화면의 ApiClient가 GET과 POST만 보내고, 같은 값을 다시 보내도 결과가 같다.
 */
@Tag(name = "창고(직원)")
@RestController
@RequestMapping("/api/v1/staff/items")
@RequiredArgsConstructor
public class WarehouseController {

	private static final String ITEM_NOT_FOUND = "창고 물품이 없음";

	private final WarehouseService warehouseService;

	@Operation(summary = "창고 물품 목록 (직원)")
	@ApiResponse(responseCode = "200", description = "물품과 1인당 개수, 현재 수량 목록. 없으면 빈 목록")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@GetMapping
	public List<ItemResponse> getItems() {
		return warehouseService.getItems().stream().map(ItemResponse::from).toList();
	}

	@Operation(summary = "새 창고 물품 등록 (직원)",
			description = "이름과 테마가 같은 물품이 이미 있으면 등록하지 않고 400이다. 있는 물품에는 수량 더하기로 더한다.")
	@ApiResponse(responseCode = "200", description = "등록한 물품")
	@ApiResponse(responseCode = "400", description = "입력 오류(이름 길이, 1인당 개수가 0~100개 밖, 1개보다 적은 수량 등), 이름과 테마가 같은 물품이 이미 있음")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@PostMapping
	public ItemResponse registerItem(@Valid @RequestBody RegisterItemRequest request) {
		return ItemResponse.from(warehouseService.registerItem(request.itemName(), request.theme(), request.perPerson(),
				request.quantity()));
	}

	@Operation(summary = "창고 물품 수량 더하기 (직원)")
	@ApiResponse(responseCode = "200", description = "더한 뒤의 물품과 현재 수량")
	@ApiResponse(responseCode = "400", description = "입력 오류(1개보다 적은 수량, 창고에 둘 수 있는 수량을 넘김, 물품 ID 형식이 올바르지 않음 등)")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ITEM_NOT_FOUND)
	@PostMapping("/{itemId}/quantity")
	public ItemResponse addQuantity(@PathVariable Long itemId, @Valid @RequestBody AddQuantityRequest request) {
		return ItemResponse.from(warehouseService.addQuantity(itemId, request.quantity()));
	}

	@Operation(summary = "창고 물품 1인당 개수 바꾸기 (직원)",
			description = "이미 줄인 수량은 다시 계산하지 않고, 이 뒤에 상품이 확정되거나 확정된 상품에 신청할 때부터 새 값으로 줄인다.")
	@ApiResponse(responseCode = "200", description = "바꾼 뒤의 물품")
	@ApiResponse(responseCode = "400", description = "입력 오류(1인당 개수가 0~100개 밖, 물품 ID 형식이 올바르지 않음 등)")
	@ApiResponse(responseCode = "401", description = ApiDescriptions.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = ApiDescriptions.NOT_STAFF)
	@ApiResponse(responseCode = "404", description = ITEM_NOT_FOUND)
	@PostMapping("/{itemId}/per-person")
	public ItemResponse changePerPerson(@PathVariable Long itemId, @Valid @RequestBody ChangePerPersonRequest request) {
		return ItemResponse.from(warehouseService.changePerPerson(itemId, request.perPerson()));
	}

}
