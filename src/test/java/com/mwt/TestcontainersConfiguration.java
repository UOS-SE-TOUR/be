package com.mwt;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL. 로컬(compose.yaml)·운영과 같은 8.4 버전을 컨테이너로 띄우고, 접속 정보는 @ServiceConnection이 넣어 준다.
 * 모듈 패키지의 DB 테스트에서도 @Import 하도록 public으로 둔다.
 *
 * <p>서버 기본 정렬 규칙은 일부러 8.4 기본값(utf8mb4_0900_ai_ci)과 다른 utf8mb4_bin(대소문자를 가린다)으로 띄운다. 문자셋·정렬
 * 규칙은 V3 마이그레이션이 정하므로, 대소문자만 다른 아이디·물품 이름을 같은 것으로 보는 테스트는 서버 설정이 달라도 통과해야 한다
 * (be #32, SchemaCollationTest).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String SERVER_COLLATION = "utf8mb4_bin";

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withCommand("--character-set-server=utf8mb4", "--collation-server=" + SERVER_COLLATION);
	}

}
