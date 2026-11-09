package com.mwt.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.booking.RecordingSmsSender.Sent;
import com.mwt.common.exception.BusinessException;
import com.mwt.notification.SmsSender;
import com.mwt.tour.RegisterTourRequest;
import com.mwt.tour.RegisterTourRequest.PriceInput;
import com.mwt.tour.RegisterTourRequest.TransportInput;
import com.mwt.tour.Theme;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourService;
import com.mwt.tour.TourStatus;

/**
 * 신청(BookingService.applyTour)의 트랜잭션 동작을 MySQL(Testcontainers)에서 스레드 여럿으로 본다(설계산출물 5.4.1).
 * 되돌려진 신청, 인원을 더할 때 정원을 넘은 신청, 같은 상품의 동시 신청, 확정 문자 받을 사람.
 * "오늘"은 2026-11-10(서울)로 고정하고, 문자는 보내지 않고 RecordingSmsSender에 기록한다.
 * 트랜잭션마다 커밋하므로 테스트가 끝나면 행을 지운다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingServiceTest {

	@TestBean
	Clock clock;

	@TestBean
	SmsSender smsSender;

	@Autowired
	BookingService bookingService;

	@Autowired
	TourService tourService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	}

	static SmsSender smsSender() {
		return new RecordingSmsSender();
	}

	@AfterEach
	void deleteAll() {
		recorder().clear();
		jdbcTemplate.update("delete from booking_option");
		jdbcTemplate.update("delete from booking");
		jdbcTemplate.update("delete from tour_price");
		jdbcTemplate.update("delete from tour_option");
		jdbcTemplate.update("delete from tour");
		jdbcTemplate.update("delete from member");
	}

	@Test
	void 신청이_되돌려지면_인원도_신청도_남지_않고_문자를_보내지_않는다() {
		long tourId = register(10);
		bookingService.applyTour(member("first", "01011112222"), request(tourId, 2));
		long second = member("second", "01033334444");

		transactionTemplate.executeWithoutResult(status -> {
			assertThat(bookingService.applyTour(second, request(tourId, 1)).status()).isEqualTo(TourStatus.CONFIRMED);
			status.setRollbackOnly();
		});

		assertThat(recorder().sent()).isEmpty();
		assertThat(bookingCount(tourId)).isEqualTo(1);
		assertThat(appliedCount(tourId)).isEqualTo(2);
	}

	@Test
	void 먼저_읽은_상품에는_자리가_남아_보여도_인원을_더할_때_정원을_넘으면_저장하지_않는다() {
		long tourId = register(10);
		long me = member("me", "01011112222");
		long other = member("other", "01033334444");

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			assertThat(tourService.getTour(tourId).remainingSeats()).isEqualTo(10);   // 이 트랜잭션이 상품을 먼저 읽어 둔다
			runInOtherThread(() -> bookingService.applyTour(other, request(tourId, 9)));
			bookingService.applyTour(me, request(tourId, 2));
		})).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.getMessage()).isEqualTo("남은 자리보다 인원이 많습니다.");
			assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		});

		assertThat(bookingCount(tourId)).isEqualTo(1);
		assertThat(appliedCount(tourId)).isEqualTo(9);
	}

	@Test
	void 같은_상품에_동시에_신청해도_교착_없이_정원까지만_저장하고_확정_문자를_빠짐없이_보낸다() throws Exception {
		long tourId = register(5);
		List<Long> members = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			members.add(member("member" + i, "0101000000" + i));
		}

		List<Object> results = runConcurrently(members, memberId -> bookingService.applyTour(memberId, request(tourId, 1)));

		assertThat(results).filteredOn(ApplyTourResponse.class::isInstance).hasSize(5);
		assertThat(results).filteredOn(BusinessException.class::isInstance)
			.hasSize(3)
			.extracting(result -> ((BusinessException) result).getMessage())
			.containsOnly("마감된 상품입니다.");
		assertThat(bookingCount(tourId)).isEqualTo(5);
		assertThat(appliedCount(tourId)).isEqualTo(5);
		// 셋째 신청이 확정해 그때까지의 셋에게, 넷째·다섯째는 이미 확정이라 자기에게만 보낸다
		List<String> phones = jdbcTemplate.queryForList("""
				select m.phone from booking b join member m on m.id = b.member_id
				where b.tour_id = ? order by b.id""", String.class, tourId);
		assertThat(recorder().sent()).extracting(Sent::phoneNumbers)
			.containsExactlyInAnyOrder(phones.subList(0, 3), List.of(phones.get(3)), List.of(phones.get(4)));
	}

	/**
	 * 셋째 신청은 상품을 읽은 뒤 둘째 신청의 잠금을 기다리고, 둘째가 커밋된 뒤 상품을 확정한다.
	 * 문자 받을 신청자를 잠그지 않고 읽으면(findByTourId) MySQL이 기다리기 전의 값을 줘서 둘째가 빠진다.
	 */
	@Test
	void 상품_잠금을_기다리는_사이_커밋된_신청자도_확정_문자를_받는다() throws Exception {
		long tourId = register(10);
		bookingService.applyTour(member("first", "01011112222"), request(tourId, 1));
		long second = member("second", "01033334444");
		long third = member("third", "01055556666");
		CountDownLatch added = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> secondApply = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				bookingService.applyTour(second, request(tourId, 1));   // 상품을 잠근 채 커밋을 미룬다
				added.countDown();
				await(commit);
			}));
			assertThat(added.await(30, TimeUnit.SECONDS)).isTrue();

			Future<ApplyTourResponse> thirdApply = executor.submit(() -> bookingService.applyTour(third, request(tourId, 1)));
			Thread.sleep(500);
			assertThat(thirdApply.isDone()).as("셋째 신청은 상품을 읽은 뒤 둘째의 잠금을 기다린다").isFalse();

			commit.countDown();
			secondApply.get(30, TimeUnit.SECONDS);
			assertThat(thirdApply.get(30, TimeUnit.SECONDS).status()).isEqualTo(TourStatus.CONFIRMED);
		} finally {
			commit.countDown();
			executor.shutdownNow();
		}

		assertThat(recorder().sent()).extracting(Sent::phoneNumbers)
			.containsExactly(List.of("01011112222", "01033334444", "01055556666"));
	}

	private RecordingSmsSender recorder() {
		return (RecordingSmsSender) smsSender;
	}

	/** 골프 그랜드, 옵션 없음. */
	private static QuoteRequest request(long tourId, int headcount) {
		return new QuoteRequest(tourId, TourLevel.GRAND, headcount, List.of());
	}

	/** 2026-12-01 출발 5일 골프 상품. 문자 문구의 상품 이름은 "골프 챌린지 투어 12/1 출발 5일"이다. */
	private long register(int capacity) {
		return tourService.registerTour(new RegisterTourRequest(Theme.GOLF, LocalDate.of(2026, 12, 1), 5, capacity,
				List.of(new PriceInput(TourLevel.CLASSIC, 900_000L), new PriceInput(TourLevel.GRAND, 1_500_000L),
						new PriceInput(TourLevel.PREMIUM, 2_500_000L)),
				List.of(new TransportInput("비행기 이코노미석", 0L))));
	}

	private long member(String loginId, String phone) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					insert into member (login_id, password, name, address, phone, member_type)
					values (?, 'encoded', '고객', '서울', ?, 'CUSTOMER')""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, loginId);
			statement.setString(2, phone);
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	private int bookingCount(long tourId) {
		return jdbcTemplate.queryForObject("select count(*) from booking where tour_id = ?", Integer.class, tourId);
	}

	private int appliedCount(long tourId) {
		return jdbcTemplate.queryForObject("select applied_count from tour where id = ?", Integer.class, tourId);
	}

	/** 회원마다 스레드 하나로 한꺼번에 시작하고, 돌려준 값이나 던진 예외를 모은다. */
	private static List<Object> runConcurrently(List<Long> memberIds, Function<Long, Object> task)
			throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Long memberId : memberIds) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.apply(memberId);
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

}
