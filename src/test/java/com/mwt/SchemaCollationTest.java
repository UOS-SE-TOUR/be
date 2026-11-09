package com.mwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 문자셋·정렬 규칙은 MySQL 서버 설정이 아니라 마이그레이션(V3)이 정한다(be #32). 테스트 MySQL은 서버 기본값을 일부러 다르게
 * 띄우므로(TestcontainersConfiguration), 여기서 맞으면 서버 설정과 상관없이 맞다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaCollationTest {

	private static final String CHARSET = "utf8mb4";
	private static final String COLLATION = "utf8mb4_0900_ai_ci";

	/** Flyway가 만들고 관리하는 기록 테이블이라 서버 기본값을 따른다. */
	private static final String FLYWAY_HISTORY = "flyway_schema_history";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 테스트_서버의_기본_정렬_규칙은_마이그레이션이_정한_규칙과_다르다() {
		assertThat(jdbcTemplate.queryForObject("SELECT @@collation_server", String.class))
			.isEqualTo(TestcontainersConfiguration.SERVER_COLLATION)
			.isNotEqualTo(COLLATION);
	}

	@Test
	void DB_기본값은_마이그레이션이_정한_문자셋과_정렬_규칙이다() {
		// 뒤에 더하는 테이블이 CREATE TABLE에 규칙을 적지 않아도 이 값을 물려받는다
		assertThat(jdbcTemplate.queryForObject("""
				SELECT CONCAT(default_character_set_name, ' ', default_collation_name)
				FROM information_schema.schemata WHERE schema_name = DATABASE()""", String.class))
			.isEqualTo(CHARSET + " " + COLLATION);
	}

	@Test
	void 모든_테이블과_문자열_칸은_마이그레이션이_정한_문자셋과_정렬_규칙이다() {
		assertThat(jdbcTemplate.queryForList("""
				SELECT CONCAT(table_name, ' ', table_collation) FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> ?
				  AND table_collation <> ?""", String.class, FLYWAY_HISTORY, COLLATION))
			.isEmpty();
		assertThat(jdbcTemplate.queryForList("""
				SELECT CONCAT(table_name, '.', column_name, ' ', character_set_name, ' ', collation_name)
				FROM information_schema.columns
				WHERE table_schema = DATABASE() AND table_name <> ? AND collation_name IS NOT NULL
				  AND (character_set_name <> ? OR collation_name <> ?)""", String.class, FLYWAY_HISTORY, CHARSET, COLLATION))
			.isEmpty();
		// 위 두 확인이 빈 결과로 그냥 지나가지 않게, 대소문자 정책이 걸린 칸 둘은 직접 본다
		assertThat(jdbcTemplate.queryForList("""
				SELECT CONCAT(table_name, '.', column_name, ' ', collation_name) FROM information_schema.columns
				WHERE table_schema = DATABASE() AND (table_name, column_name) IN (('member', 'login_id'), ('item', 'name'))
				ORDER BY table_name""", String.class))
			.containsExactly("item.name " + COLLATION, "member.login_id " + COLLATION);
	}

}
