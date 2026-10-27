package com.mwt;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL. 로컬(compose.yaml)·운영과 같은 8.4 버전을 컨테이너로 띄우고, 접속 정보는 @ServiceConnection이 넣어 준다.
 * 모듈 패키지의 DB 테스트에서도 @Import 하도록 public으로 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

}
