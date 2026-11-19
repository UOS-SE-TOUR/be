package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 등록할 때 난 무결성 오류 가운데 (이름, 테마) 유일 키 위반만 "이미 있는 물품" 400으로 보는지. DB 없이 본다(실제 겹침은 WarehouseServiceTest).
 */
class WarehouseServiceDuplicateItemTest {

	@ParameterizedTest
	@ValueSource(strings = {"uk_item_name_theme", "item.uk_item_name_theme", "ITEM.UK_ITEM_NAME_THEME"})
	void 이름_테마_유일_키_위반이면_이미_있는_물품으로_본다(String constraintName) {
		assertThat(WarehouseService.isDuplicateItem(violation(constraintName))).isTrue();
	}

	@Test
	void 다른_무결성_오류는_이미_있는_물품으로_보지_않는다() {
		assertThat(WarehouseService.isDuplicateItem(violation("uk_member_login_id"))).isFalse();
		assertThat(WarehouseService.isDuplicateItem(violation(null))).isFalse();
		assertThat(WarehouseService.isDuplicateItem(new DataIntegrityViolationException("Data too long"))).isFalse();
	}

	private static DataIntegrityViolationException violation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException("Duplicate entry",
				new SQLException("Duplicate entry", "23000", 1062), constraintName);
		return new DataIntegrityViolationException("could not execute statement", cause);
	}

}
