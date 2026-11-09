package com.mwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 서버 전체를 MySQL(Testcontainers)에 붙여 띄워 본다. Flyway 마이그레이션과 엔티티 검증(validate)까지 여기서 걸린다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TourServerApplicationTests {

	@Autowired
	ScheduledTaskHolder scheduledTaskHolder;

	@Test
	void contextLoads() {
	}

	@Test
	void 여행_상품_상태를_바꾸는_예약_작업이_켜져_있다() {
		assertThat(scheduledTaskHolder.getScheduledTasks())
			.extracting(ScheduledTask::getTask)
			.filteredOn(CronTask.class::isInstance)
			.anySatisfy(task -> {
				assertThat(((CronTask) task).getExpression()).isEqualTo("0 0 0 * * *");
				assertThat(task.getRunnable()).hasToString("com.mwt.tour.TourScheduler.updateStatuses");
			});
	}

}
