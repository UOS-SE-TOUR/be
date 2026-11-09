package com.mwt.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.common.exception.BusinessException;

/**
 * 신청 인원 더하기(TourService.addApplicants)가 상품을 잠가 차례로 더하는지 MySQL(Testcontainers)에서 스레드 여럿으로 본다.
 * 트랜잭션마다 커밋하므로 테스트가 끝나면 상품을 지운다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TourConcurrencyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	@Autowired
	TourService tourService;

	@Autowired
	TourRepository tourRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final List<Long> savedTourIds = new ArrayList<>();

	/** 같은 DB를 쓰는 다른 테스트의 행은 두고, 이 테스트가 만든 상품만 지운다. */
	@AfterEach
	void deleteTours() {
		tourRepository.deleteAllById(savedTourIds);
	}

	@Test
	void 동시에_들어온_신청이_정원을_넘지_않는다() throws Exception {
		Long tourId = save("2026-11-20", 5, 0);

		List<Object> results = runConcurrently(8, () -> addInOwnTransaction(tourId, 1));

		assertThat(results).filteredOn(ConfirmChange.class::isInstance).hasSize(5);
		assertThat(results).filteredOn(ConfirmChange.NOT_CONFIRMED::equals).hasSize(2);
		assertThat(results).filteredOn(ConfirmChange.NEWLY_CONFIRMED::equals).hasSize(1);
		assertThat(results).filteredOn(ConfirmChange.ALREADY_CONFIRMED::equals).hasSize(2);
		assertThat(results).filteredOn(BusinessException.class::isInstance)
			.hasSize(3)
			.extracting(result -> ((BusinessException) result).getMessage())
			.containsOnly("마감된 상품입니다.");
		Tour tour = tourRepository.findById(tourId).orElseThrow();
		assertThat(tour.getAppliedCount()).isEqualTo(5);
		assertThat(tour.getStatus()).isEqualTo(TourStatus.FULL);
	}

	@Test
	void 같은_트랜잭션에서_먼저_읽은_상품이어도_다른_신청이_더한_인원까지_보고_더한다() throws Exception {
		Long tourId = save("2026-11-20", 10, 0);

		ConfirmChange change = transactionTemplate.execute(status -> {
			assertThat(tourService.getTour(tourId).getAppliedCount()).isZero();   // 신청이 상품을 먼저 읽어 확인하는 흐름
			runInOtherThread(() -> addInOwnTransaction(tourId, 2));
			return tourService.addApplicants(tourId, 1);
		});

		assertThat(change).isEqualTo(ConfirmChange.NEWLY_CONFIRMED);
		Tour tour = tourRepository.findById(tourId).orElseThrow();
		assertThat(tour.getAppliedCount()).isEqualTo(3);
		assertThat(tour.getStatus()).isEqualTo(TourStatus.CONFIRMED);
	}

	@Test
	void 같은_트랜잭션에서_두_번_더해도_앞서_더한_인원이_사라지지_않는다() throws Exception {
		Long tourId = save("2026-11-20", 10, 0);

		transactionTemplate.executeWithoutResult(status -> {
			tourService.getTour(tourId);
			runInOtherThread(() -> addInOwnTransaction(tourId, 2));
			assertThat(tourService.addApplicants(tourId, 1)).isEqualTo(ConfirmChange.NEWLY_CONFIRMED);
			assertThat(tourService.addApplicants(tourId, 1)).isEqualTo(ConfirmChange.ALREADY_CONFIRMED);
		});

		assertThat(tourRepository.findById(tourId).orElseThrow().getAppliedCount()).isEqualTo(4);
	}

	@Test
	void 신청_트랜잭션_밖에서_부르면_인원을_더하지_않고_바로_실패한다() {
		Long tourId = save("2026-11-20", 10, 0);

		assertThatThrownBy(() -> tourService.addApplicants(tourId, 1)).isInstanceOf(IllegalTransactionStateException.class);
		assertThat(tourRepository.findById(tourId).orElseThrow().getAppliedCount()).isZero();
	}

	@Test
	void 자정_예약_작업은_끝나지_않은_신청을_기다렸다가_더해진_인원으로_출발_처리한다() throws Exception {
		Long tourId = save("2026-11-10", 10, 2);
		CountDownLatch added = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> apply = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				tourService.addApplicants(tourId, 1);   // 전날 확인을 마친 신청이 자정을 넘겨 더한다
				added.countDown();
				await(commit);
			}));
			assertThat(added.await(30, TimeUnit.SECONDS)).isTrue();

			Future<?> scheduler = executor.submit(() -> tourService.updateStatuses(TODAY));
			Thread.sleep(500);
			assertThat(scheduler.isDone()).as("신청 트랜잭션이 상품을 잠근 동안 예약 작업은 기다린다").isFalse();

			commit.countDown();
			apply.get(30, TimeUnit.SECONDS);
			scheduler.get(30, TimeUnit.SECONDS);
		} finally {
			commit.countDown();
			executor.shutdownNow();
		}

		Tour tour = tourRepository.findById(tourId).orElseThrow();
		assertThat(tour.getAppliedCount()).isEqualTo(3);
		assertThat(tour.getStatus()).isEqualTo(TourStatus.IN_PROGRESS);
	}

	/** 골프 상품을 만들고 신청 인원을 더해 저장한다. 출발일 하루 전을 "오늘"로 넣어 오늘인 출발일도 저장할 수 있다. */
	private Long save(String departureDate, int capacity, int applicants) {
		LocalDate date = LocalDate.parse(departureDate);
		Tour tour = new Tour(Theme.GOLF, date, 5, capacity, date.minusDays(1));
		if (applicants > 0) {
			tour.addApplicants(applicants);
		}
		Long tourId = tourRepository.save(tour).getId();
		savedTourIds.add(tourId);
		return tourId;
	}

	/** 신청 한 건처럼 자기 트랜잭션에서 인원을 더하고 커밋한다. */
	private ConfirmChange addInOwnTransaction(Long tourId, int headcount) {
		return transactionTemplate.execute(status -> tourService.addApplicants(tourId, headcount));
	}

	/** 같은 일을 스레드마다 하나씩 한꺼번에 시작하고, 돌려준 값이나 던진 예외를 모은다. */
	private static List<Object> runConcurrently(int threads, Task task) throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.run();
				}));
			}
			start.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				try {
					results.add(Objects.requireNonNull(future.get(30, TimeUnit.SECONDS)));
				} catch (ExecutionException e) {
					results.add(e.getCause());
				} catch (TimeoutException e) {
					throw new AssertionError("동시 신청이 30초 안에 끝나지 않았다", e);
				}
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	/** 다른 스레드(다른 트랜잭션)에서 돌리고 커밋될 때까지 기다린다. */
	private static void runInOtherThread(Runnable work) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			executor.submit(work).get(30, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new AssertionError("다른 트랜잭션의 신청이 끝나지 않았다", e);
		} finally {
			executor.shutdownNow();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(30, TimeUnit.SECONDS)) {
				throw new AssertionError("30초 안에 풀리지 않았다");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

	@FunctionalInterface
	private interface Task {

		Object run() throws Exception;

	}

}
