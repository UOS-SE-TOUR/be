package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;
import com.mwt.tour.Tour;

/**
 * 창고 물품 조회, 등록, 수량 더하기, 1인당 개수 바꾸기, 확정 때 줄이기(설계산출물 5.9, be #71), 수량 기록과 늦은 취소로 되돌리기.
 * MySQL(Testcontainers)에 붙여 이름 찾기·잠금·유일 제약까지 본다. Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, WarehouseServiceTest.PauseAfterNameCheck.class})
@SpringBootTest
class WarehouseServiceTest {

	private static final String DUPLICATE_ITEM = "이미 있는 물품입니다. 목록에서 수량을 더해 주세요.";

	@Autowired
	WarehouseService warehouseService;

	@Autowired
	ItemRepository itemRepository;

	@Autowired
	ItemHistoryRepository itemHistoryRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@AfterEach
	void deleteItems() {
		PauseAfterNameCheck.BARRIER.set(null);
		itemRepository.deleteAll();
		itemHistoryRepository.deleteAll();
	}

	@Test
	void 수량이_바뀔_때마다_이유와_바뀐_양_바뀐_뒤_수량을_기록하고_1인당_0개는_남기지_않는다() {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();
		warehouseService.registerItem("생수", Theme.GOLF, 0, 10);
		warehouseService.addQuantity(cap, 5);
		warehouseService.changePerPerson(cap, 2);
		Tour tour = golfTour(77L);
		transactionTemplate.executeWithoutResult(status -> warehouseService.takeOutItems(tour, 3));

		assertThat(warehouseService.getHistory())
			.extracting(ItemHistory::getItemName, ItemHistory::getReason, ItemHistory::getTourId, ItemHistory::getTourTitle,
					ItemHistory::getChangeAmount, ItemHistory::getQuantityAfter)
			.containsExactly(
					tuple("모자", ItemHistoryReason.TAKE_OUT, 77L, "골프 챌린지 투어 12/1 출발 5일", -6, 9),
					tuple("모자", ItemHistoryReason.ADD, null, null, 5, 15),
					tuple("생수", ItemHistoryReason.REGISTER, null, null, 10, 10),
					tuple("모자", ItemHistoryReason.REGISTER, null, null, 10, 10));
	}

	@Test
	void 늦은_취소는_그_상품_때문에_줄인_만큼만_되돌린다() {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();
		long ball = warehouseService.registerItem("골프공", Theme.GOLF, 2, 4).getId();
		Tour cancelled = golfTour(77L);
		Tour other = golfTour(78L);
		transactionTemplate.executeWithoutResult(status -> {
			warehouseService.takeOutItems(cancelled, 3);
			warehouseService.takeOutItems(other, 1);
			warehouseService.takeOutItems(cancelled, 1);   // 확정된 상품에 더 신청
		});
		warehouseService.changePerPerson(ball, 5);
		long towel = warehouseService.registerItem("타월", Theme.GOLF, 1, 10).getId();

		transactionTemplate.executeWithoutResult(status -> warehouseService.putBackItems(cancelled));

		assertThat(quantity(cap)).as("10 − 3 − 1 − 1 + 4").isEqualTo(9);
		assertThat(quantity(ball)).as("4 − 6 − 2 − 2 + 8, 바꾼 1인당 개수로 다시 세지 않는다").isEqualTo(2);
		assertThat(quantity(towel)).as("줄인 뒤 등록한 물품은 그대로").isEqualTo(10);
		assertThat(itemHistoryRepository.findByTourId(77L))
			.filteredOn(history -> history.getReason() == ItemHistoryReason.PUT_BACK)
			.extracting(ItemHistory::getItemName, ItemHistory::getChangeAmount)
			.containsExactlyInAnyOrder(tuple("모자", 4), tuple("골프공", 8));
	}

	@Test
	void 늦은_취소로_되돌리기는_트랜잭션_밖에서_부를_수_없다() {
		assertThatThrownBy(() -> warehouseService.putBackItems(golfTour(77L)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void 새_물품을_테마_1인당_개수_처음_수량과_함께_등록한다() {
		Item registered = warehouseService.registerItem(" 생수 ", Theme.GOLF, 2, 10);

		assertThat(registered.getId()).isNotNull();
		assertThat(itemRepository.findById(registered.getId()).orElseThrow())
			.extracting(Item::getName, Item::getTheme, Item::getPerPerson, Item::getQuantity)
			.containsExactly("생수", Theme.GOLF, 2, 10);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Golf Ball", "  golf ball", "GOLF BALL "})
	void 이름과_테마가_같은_물품이_있으면_등록하지_않고_400이다(String name) {
		warehouseService.registerItem("Golf Ball", Theme.GOLF, 1, 10);

		assertThatThrownBy(() -> warehouseService.registerItem(name, Theme.GOLF, 3, 5))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getMessage()).isEqualTo(DUPLICATE_ITEM);
				assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
			});

		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getPerPerson, Item::getQuantity)
			.containsExactly(tuple("Golf Ball", 1, 10));
	}

	@Test
	void 자모를_풀어_쓴_한글_이름도_같은_물품으로_보고_등록하지_않는다() {
		warehouseService.registerItem("생수", Theme.GOLF, 1, 10);

		assertThatThrownBy(() -> warehouseService.registerItem(Normalizer.normalize("생수", Normalizer.Form.NFD), Theme.GOLF, 1, 5))
			.isInstanceOf(BusinessException.class)
			.hasMessage(DUPLICATE_ITEM);

		assertThat(itemRepository.count()).isEqualTo(1);
	}

	@Test
	void 같은_이름도_테마가_다르면_따로_등록되고_수량도_따로_센다() {
		Item golf = warehouseService.registerItem("생수", Theme.GOLF, 1, 10);

		Item trekking = warehouseService.registerItem("생수", Theme.TREKKING, 2, 5);
		warehouseService.addQuantity(golf.getId(), 1);

		assertThat(trekking.getId()).isNotEqualTo(golf.getId());
		assertThat(itemRepository.findAll()).extracting(Item::getTheme, Item::getPerPerson, Item::getQuantity)
			.containsExactlyInAnyOrder(tuple(Theme.GOLF, 1, 11), tuple(Theme.TREKKING, 2, 5));
	}

	@Test
	void 규칙에_어긋나면_새_물품을_등록하지_않는다() {
		assertThatThrownBy(() -> warehouseService.registerItem("생수", null, 1, 10)).hasMessage("테마를 골라 주세요.");
		assertThatThrownBy(() -> warehouseService.registerItem("생수", Theme.GOLF, 1, 0)).hasMessage("수량은 1개 이상이어야 합니다.");
		assertThatThrownBy(() -> warehouseService.registerItem("생수", Theme.GOLF, 101, 10))
			.hasMessage("1인당 개수는 0개부터 100개까지입니다.");
		assertThatThrownBy(() -> warehouseService.registerItem("생수", Theme.GOLF, -1, 10))
			.hasMessage("1인당 개수는 0개부터 100개까지입니다.");

		assertThat(itemRepository.count()).isZero();
	}

	@Test
	void 물품_목록은_이름_순이고_같은_이름은_테마_순이다() {
		warehouseService.registerItem("커플 기념티", Theme.HONEYMOON, 1, 4);
		warehouseService.registerItem("생수", Theme.TREKKING, 1, 20);
		warehouseService.registerItem("골프공", Theme.GOLF, 1, 30);
		warehouseService.registerItem("생수", Theme.GOLF, 1, 10);

		assertThat(warehouseService.getItems()).extracting(Item::getName, Item::getTheme)
			.containsExactly(tuple("골프공", Theme.GOLF), tuple("생수", Theme.GOLF), tuple("생수", Theme.TREKKING),
					tuple("커플 기념티", Theme.HONEYMOON));
	}

	@Test
	void 두_직원이_같은_새_물품을_동시에_등록하면_하나만_남고_늦은_쪽은_400이다() throws Exception {
		PauseAfterNameCheck.pauseOnce(2);   // 둘 다 "없다"고 본 뒤에 저장한다

		List<Throwable> errors = runConcurrently(List.of(
				() -> warehouseService.registerItem("생수", Theme.GOLF, 1, 3),
				() -> warehouseService.registerItem("생수", Theme.GOLF, 1, 5)));

		assertThat(errors).filteredOn(error -> error == null).hasSize(1);
		assertThat(errors).filteredOn(error -> error != null).singleElement()
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getMessage()).isEqualTo(DUPLICATE_ITEM);
				assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
			});
		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getQuantity)
			.containsAnyOf(tuple("생수", 3), tuple("생수", 5))
			.hasSize(1);
	}

	@Test
	void 여러_직원이_이름이_다른_새_물품을_동시에_등록해도_모두_저장된다() throws Exception {
		List<String> names = List.of("모자", "물병", "목도리", "물티슈", "머리끈");
		PauseAfterNameCheck.pauseOnce(names.size());   // 모두 "없다"고 본 뒤에 저장한다

		List<Throwable> errors = runConcurrently(names.stream()
			.<Runnable>map(name -> () -> warehouseService.registerItem(name, Theme.TREKKING, 1, 1))
			.toList());

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getName).containsExactlyInAnyOrderElementsOf(names);
	}

	@Test
	void 같은_새_이름을_테마만_달리해_동시에_등록하면_테마마다_저장된다() throws Exception {
		PauseAfterNameCheck.pauseOnce(2);   // 둘 다 "없다"고 본 뒤에 저장한다

		List<Throwable> errors = runConcurrently(List.of(
				() -> warehouseService.registerItem("생수", Theme.GOLF, 1, 3),
				() -> warehouseService.registerItem("생수", Theme.TREKKING, 1, 5)));

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getTheme, Item::getQuantity)
			.containsExactlyInAnyOrder(tuple(Theme.GOLF, 3), tuple(Theme.TREKKING, 5));
	}

	@Test
	void 있는_물품에_수량을_더한다() {
		Item item = warehouseService.registerItem("생수", Theme.GOLF, 2, 10);

		Item added = warehouseService.addQuantity(item.getId(), 5);

		assertThat(added.getQuantity()).isEqualTo(15);
		assertThat(added.getPerPerson()).isEqualTo(2);
		assertThat(itemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(15);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -3})
	void 더할_수량이_1보다_작으면_400이고_수량은_그대로다(int quantity) {
		Item item = warehouseService.registerItem("생수", Theme.GOLF, 1, 10);

		assertThatThrownBy(() -> warehouseService.addQuantity(item.getId(), quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");

		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(10);
	}

	@Test
	void 일인당_개수를_바꾼다() {
		Item item = warehouseService.registerItem("생수", Theme.GOLF, 1, 10);

		Item changed = warehouseService.changePerPerson(item.getId(), 0);

		assertThat(changed.getPerPerson()).isZero();
		assertThat(itemRepository.findById(item.getId()).orElseThrow())
			.extracting(Item::getPerPerson, Item::getQuantity)
			.containsExactly(0, 10);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 101})
	void 일인당_개수가_범위_밖이면_400이고_그대로다(int perPerson) {
		Item item = warehouseService.registerItem("생수", Theme.GOLF, 3, 10);

		assertThatThrownBy(() -> warehouseService.changePerPerson(item.getId(), perPerson))
			.isInstanceOf(BusinessException.class)
			.hasMessage("1인당 개수는 0개부터 100개까지입니다.");

		assertThat(itemRepository.findAll()).extracting(Item::getPerPerson).containsExactly(3);
	}

	@Test
	void 없는_물품에_더하거나_바꾸면_404다() {
		long missing = warehouseService.registerItem("생수", Theme.GOLF, 1, 10).getId() + 1000;

		for (Runnable call : List.<Runnable>of(() -> warehouseService.addQuantity(missing, 1),
				() -> warehouseService.changePerPerson(missing, 1))) {
			assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getMessage()).isEqualTo("창고 물품을 찾을 수 없습니다.");
				assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
			});
		}
	}

	@Test
	void 여러_직원이_같은_물품에_동시에_더해도_수량을_잃지_않는다() throws Exception {
		long itemId = warehouseService.registerItem("생수", Theme.GOLF, 1, 100).getId();

		List<Throwable> errors = runConcurrently(Collections.nCopies(8, () -> warehouseService.addQuantity(itemId, 1)));

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(108);
	}

	@Test
	void 확정_때_그_테마_물품만_인원_곱하기_1인당_개수만큼_줄이고_모자라면_음수가_된다() {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();
		long ball = warehouseService.registerItem("골프공", Theme.GOLF, 2, 4).getId();
		long water = warehouseService.registerItem("생수", Theme.GOLF, 0, 10).getId();
		long scarf = warehouseService.registerItem("스카프", Theme.TREKKING, 1, 10).getId();

		transactionTemplate.executeWithoutResult(status -> warehouseService.takeOutItems(golfTour(), 3));

		assertThat(quantity(cap)).isEqualTo(7);
		assertThat(quantity(ball)).as("4 − 3 × 2, 모자란 만큼 음수").isEqualTo(-2);
		assertThat(quantity(water)).as("1인당 0개는 그대로").isEqualTo(10);
		assertThat(quantity(scarf)).as("다른 테마는 그대로").isEqualTo(10);
	}

	@Test
	void 그_테마_물품이_없으면_아무것도_줄지_않는다() {
		long scarf = warehouseService.registerItem("스카프", Theme.TREKKING, 1, 10).getId();

		transactionTemplate.executeWithoutResult(status -> warehouseService.takeOutItems(golfTour(), 3));

		assertThat(quantity(scarf)).isEqualTo(10);
	}

	@Test
	void 확정_때_줄이기는_트랜잭션_밖에서_부를_수_없다() {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();

		assertThatThrownBy(() -> warehouseService.takeOutItems(golfTour(), 3))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(quantity(cap)).isEqualTo(10);
	}

	@Test
	void 확정_때_줄인_트랜잭션이_되돌려지면_수량도_되돌려진다() {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();

		transactionTemplate.executeWithoutResult(status -> {
			warehouseService.takeOutItems(golfTour(), 3);
			status.setRollbackOnly();
		});

		assertThat(quantity(cap)).isEqualTo(10);
	}

	/**
	 * 확정 때 줄이는 트랜잭션이 그 테마 물품을 잠근 채 커밋을 미루는 동안, 직원의 수량 더하기는 잠금을 기다린다.
	 * 커밋된 뒤에 줄어든 수량에 더하므로 어느 쪽도 사라지지 않는다.
	 */
	@Test
	void 확정_때_줄이는_사이_직원이_더해도_한쪽이_사라지지_않는다() throws Exception {
		long cap = warehouseService.registerItem("모자", Theme.GOLF, 1, 10).getId();
		CountDownLatch taken = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> takeOut = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				warehouseService.takeOutItems(golfTour(), 3);
				taken.countDown();
				await(commit);
			}));
			assertThat(taken.await(30, TimeUnit.SECONDS)).isTrue();

			Future<Item> add = executor.submit(() -> warehouseService.addQuantity(cap, 5));
			Thread.sleep(500);
			assertThat(add.isDone()).as("수량 더하기는 줄이는 트랜잭션의 잠금을 기다린다").isFalse();

			commit.countDown();
			takeOut.get(30, TimeUnit.SECONDS);
			assertThat(add.get(30, TimeUnit.SECONDS).getQuantity()).isEqualTo(12);
		} finally {
			commit.countDown();
			executor.shutdownNow();
		}

		assertThat(quantity(cap)).isEqualTo(12);
	}

	/**
	 * 물품 대부분이 그 테마여도 확정 때 줄이기는 테마 색인으로 그 테마 물품만 잠근다. 그럴 때 MySQL은 색인보다 테이블 전체를 훑기를
	 * 골라 다른 테마 물품까지 잠그고, 그러면 아래 더하기가 기다린다.
	 */
	@Test
	void 확정_때_줄이는_사이에도_다른_테마_물품은_기다리지_않고_더한다() throws Exception {
		for (int i = 0; i < 20; i++) {
			warehouseService.registerItem("모자" + i, Theme.GOLF, 1, 10);
		}
		long scarf = warehouseService.registerItem("스카프", Theme.TREKKING, 1, 10).getId();
		CountDownLatch taken = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> takeOut = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				warehouseService.takeOutItems(golfTour(), 3);
				taken.countDown();
				await(commit);
			}));
			assertThat(taken.await(30, TimeUnit.SECONDS)).isTrue();

			Future<Item> add = executor.submit(() -> warehouseService.addQuantity(scarf, 5));
			assertThat(add.get(10, TimeUnit.SECONDS).getQuantity()).as("GOLF를 잠근 동안에도 TREKKING 물품은 바로 더한다").isEqualTo(15);

			commit.countDown();
			takeOut.get(30, TimeUnit.SECONDS);
		} finally {
			commit.countDown();
			executor.shutdownNow();
		}
	}

	/** 정원 10명, 12/1 출발 5일인 골프 상품(저장하지 않는다). 기록에 붙는 상품 ID는 id로 준다. */
	private static Tour golfTour(Long id) {
		Tour tour = new Tour(Theme.GOLF, LocalDate.of(2026, 12, 1), 5, 10, LocalDate.of(2026, 11, 10));
		ReflectionTestUtils.setField(tour, "id", id);
		return tour;
	}

	private static Tour golfTour() {
		return golfTour(null);
	}

	private int quantity(long itemId) {
		return itemRepository.findById(itemId).orElseThrow().getQuantity();
	}

	/** 일을 스레드마다 하나씩 한꺼번에 시작하고, 일마다 던진 예외를 순서대로 모은다(성공이면 null). */
	private static List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					task.run();
					return null;
				}));
			}
			start.countDown();
			List<Throwable> errors = new ArrayList<>();
			for (Future<?> future : futures) {
				try {
					future.get(30, TimeUnit.SECONDS);
					errors.add(null);
				} catch (ExecutionException e) {
					errors.add(e.getCause());
				} catch (TimeoutException e) {
					throw new AssertionError("동시 요청이 30초 안에 끝나지 않았다", e);
				}
			}
			return errors;
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

	/**
	 * pauseOnce(n)을 부르면 ItemRepository.existsByNameAndTheme이 끝난 뒤 n개 스레드가 모두 거기까지 올 때까지 한 번 기다린다.
	 * 요청들이 모두 "같은 이름이 없다"고 본 다음에 저장하게 만들어, 드물게만 생기는 겹침을 매번 일으킨다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class PauseAfterNameCheck {

		static final AtomicReference<CyclicBarrier> BARRIER = new AtomicReference<>();

		static void pauseOnce(int threads) {
			BARRIER.set(new CyclicBarrier(threads, () -> BARRIER.set(null)));
		}

		@Bean
		static BeanPostProcessor pauseAfterNameCheck() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof ItemRepository repository)) {
						return bean;
					}
					return Proxy.newProxyInstance(ItemRepository.class.getClassLoader(),
							new Class<?>[] {ItemRepository.class}, (proxy, method, args) -> {
								Object result;
								try {
									result = method.invoke(repository, args);
								} catch (InvocationTargetException e) {
									throw e.getCause();
								}
								CyclicBarrier barrier = BARRIER.get();
								if (barrier != null && method.getName().equals("existsByNameAndTheme")) {
									barrier.await(10, TimeUnit.SECONDS);
								}
								return result;
							});
				}
			};
		}

	}

}
