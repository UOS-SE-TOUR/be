package com.mwt.warehouse;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 직원의 창고 물품 조회와 추가 요청을 받는다(설계산출물 4.8, 5.9).
 */
@RestController
@RequestMapping("/api/v1/staff/items")
@RequiredArgsConstructor
public class WarehouseController {

	private final WarehouseService warehouseService;

	@GetMapping
	public List<ItemResponse> getItems() {
		return warehouseService.getItems().stream().map(ItemResponse::from).toList();
	}

	/** 추가한 뒤의 물품(현재 수량 포함)을 돌려준다. 새로 만들었든 수량만 더했든 200이다. */
	@PostMapping
	public ItemResponse addItem(@Valid @RequestBody AddItemRequest request) {
		return ItemResponse.from(warehouseService.addItem(request.itemName(), request.theme(), request.quantity()));
	}

}
