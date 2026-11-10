package com.mwt.warehouse;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mwt.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 창고 물품 조회와 추가 요청을 받는다(설계산출물 4.8, 5.9).
 */
@Tag(name = "창고(직원)")
@RestController
@RequestMapping("/api/v1/staff/items")
@RequiredArgsConstructor
public class WarehouseController {

	private final WarehouseService warehouseService;

	@Operation(summary = "창고 물품 목록 (직원)")
	@ApiResponse(responseCode = "200", description = "물품과 현재 수량 목록. 없으면 빈 목록")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_STAFF)
	@GetMapping
	public List<ItemResponse> getItems() {
		return warehouseService.getItems().stream().map(ItemResponse::from).toList();
	}

	/** 추가한 뒤의 물품(현재 수량 포함)을 돌려준다. 새로 만들었든 수량만 더했든 200이다. */
	@Operation(summary = "창고 물품 추가 (직원)",
			description = "이름과 테마가 같은 물품이 있으면 수량만 더하고, 없으면 새로 만든다. 어느 쪽이든 200이다.")
	@ApiResponse(responseCode = "200", description = "추가한 뒤의 물품과 현재 수량")
	@ApiResponse(responseCode = "400", description = "입력 오류(이름 길이, 1개보다 적은 수량, 창고에 둘 수 있는 수량을 넘김 등)")
	@ApiResponse(responseCode = "401", description = OpenApiConfig.UNAUTHORIZED)
	@ApiResponse(responseCode = "403", description = OpenApiConfig.NOT_STAFF)
	@PostMapping
	public ItemResponse addItem(@Valid @RequestBody AddItemRequest request) {
		return ItemResponse.from(warehouseService.addItem(request.itemName(), request.theme(), request.quantity()));
	}

}
