package com.mwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 서버 전체를 MySQL(Testcontainers)에 붙여 띄워 본다. Flyway 마이그레이션과 엔티티 검증(validate)까지 여기서 걸린다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TourServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
