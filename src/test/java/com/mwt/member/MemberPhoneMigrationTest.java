package com.mwt.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.mwt.TestcontainersConfiguration;
import com.mwt.notification.SmsSender;

/**
 * V4 마이그레이션이 입력한 꼴 그대로 저장돼 있던 연락처를 숫자만 남긴 국내 번호로 바꾸는지 본다(be #66).
 * 테스트 DB는 서버가 뜰 때 V4까지 다 올라가 바꿀 행이 없으므로, 같은 MySQL에 DB를 따로 만들어 V3까지 올리고 옛 꼴의 연락처를
 * 넣은 뒤 V4를 돌린다. DB를 만들어야 해서 root로 붙는다(Testcontainers는 root 비밀번호를 테스트 계정 비밀번호와 같게 둔다).
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberPhoneMigrationTest {

	private static final String SCHEMA = "member_phone_migration_test";

	@Autowired
	MySQLContainer mysql;

	@AfterEach
	void dropSchema() {
		rootJdbcTemplate().execute("DROP DATABASE IF EXISTS " + SCHEMA);
	}

	@Test
	void V4는_입력한_꼴_그대로인_연락처를_숫자만_남긴_국내_번호로_바꾼다() {
		Map<String, String> phones = new LinkedHashMap<>();   // 넣는 연락처 → V4 뒤의 연락처
		phones.put("010-1234-5678", "01012345678");
		phones.put("010 1234 5678", "01012345678");
		phones.put("+82 10-1234-5678", "01012345678");
		phones.put("+82 010-1234-5678", "01012345678");   // 82 뒤의 0을 남겨 적은 꼴 — 0010…이 되면 안 된다
		phones.put("+82 10 - 1234 - 5678", "01012345678");   // 칸 길이(20자)를 꽉 채운 꼴
		phones.put("82-010-1234-5678", "01012345678");
		phones.put("+010-1234-5678", "01012345678");   // 이제 가입에서 거절하지만 그 전에 가입한 회원은 남는다
		phones.put("011-123-4567", "0111234567");
		phones.put("01012345678", "01012345678");   // 이미 이 꼴이면 그대로다
		assertThat(flyway("3").migrate().targetSchemaVersion).isEqualTo("3");
		JdbcTemplate jdbc = rootJdbcTemplate();
		int n = 0;
		for (String phone : phones.keySet()) {
			jdbc.update("insert into " + SCHEMA + ".member (login_id, password, name, address, phone, member_type)"
					+ " values (?, 'encoded', '고객', '서울', ?, 'CUSTOMER')", "member" + n++, phone);
		}

		MigrateResult result = flyway("4").migrate();

		assertThat(result.migrationsExecuted).isOne();
		assertThat(result.targetSchemaVersion).isEqualTo("4");
		Map<String, String> migrated = new LinkedHashMap<>();
		n = 0;
		for (String phone : phones.keySet()) {
			migrated.put(phone, jdbc.queryForObject("select phone from " + SCHEMA + ".member where login_id = ?",
					String.class, "member" + n++));
		}
		assertThat(migrated).containsExactlyEntriesOf(phones);
		phones.forEach((phone, normalized) -> assertThat(SmsSender.normalizeNumber(phone)).as(phone).isEqualTo(normalized));
	}

	/** SCHEMA를 만들고(없으면) target 버전까지 마이그레이션을 올리는 Flyway. 마이그레이션은 서버가 쓰는 것(db/migration) 그대로다. */
	private Flyway flyway(String target) {
		return Flyway.configure()
			.dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword())
			.schemas(SCHEMA)
			.target(target)
			.load();
	}

	private JdbcTemplate rootJdbcTemplate() {
		return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
	}

}
