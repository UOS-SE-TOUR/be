package com.mwt.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mwt.TestcontainersConfiguration;
import com.mwt.common.exception.BusinessException;
import com.mwt.tour.Theme;

/**
 * 창고 물품 조회·추가(설계산출물 5.9). MySQL(Testcontainers)에 붙여 이름 찾기·잠금·유일 제약까지 본다.
 * Docker가 꺼져 있으면 건너뛴다(CI에서는 항상 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, WarehouseServiceTest.PauseAfterFindByName.class})
@SpringBootTest
class WarehouseServiceTest {

	@Autowired
	WarehouseService warehouseService;

	@Autowired
	ItemRepository itemRepository;

	@AfterEach
	void deleteItems() {
		PauseAfterFindByName.BARRIER.set(null);
		itemRepository.deleteAll();
	}

	@Test
	void 없는_물품은_테마와_함께_새로_만든다() {
		Item added = warehouseService.addItem(" 생수 ", Theme.GOLF, 10);

		assertThat(added.getId()).isNotNull();
		Item saved = itemRepository.findById(added.getId()).orElseThrow();
		assertThat(saved.getName()).isEqualTo("생수");
		assertThat(saved.getTheme()).isEqualTo(Theme.GOLF);
		assertThat(saved.getQuantity()).isEqualTo(10);
	}

	@Test
	void 같은_이름의_물품이_있으면_수량만_더하고_테마는_그대로_둔다() {
		Item first = warehouseService.addItem("생수", Theme.GOLF, 10);

		Item added = warehouseService.addItem("  생수", Theme.TREKKING, 5);

		assertThat(added.getId()).isEqualTo(first.getId());
		assertThat(added.getQuantity()).isEqualTo(15);
		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getTheme, Item::getQuantity)
			.containsExactly(tuple("생수", Theme.GOLF, 15));
	}

	@Test
	void 대소문자만_다른_이름은_DB_정렬_규칙대로_같은_물품이다() {
		warehouseService.addItem("Golf Ball", Theme.GOLF, 10);

		Item added = warehouseService.addItem("golf ball", Theme.GOLF, 5);

		assertThat(added.getName()).isEqualTo("Golf Ball");
		assertThat(added.getQuantity()).isEqualTo(15);
		assertThat(itemRepository.count()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -3})
	void 수량이_1보다_작으면_새_물품을_만들지_않는다(int quantity) {
		assertThatThrownBy(() -> warehouseService.addItem("생수", Theme.GOLF, quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");

		assertThat(itemRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -3})
	void 수량이_1보다_작으면_있는_물품의_수량을_바꾸지_않는다(int quantity) {
		warehouseService.addItem("생수", Theme.GOLF, 10);

		assertThatThrownBy(() -> warehouseService.addItem("생수", Theme.GOLF, quantity))
			.isInstanceOf(BusinessException.class)
			.hasMessage("수량은 1개 이상이어야 합니다.");

		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(10);
	}

	@Test
	void 물품_목록은_이름_순이다() {
		warehouseService.addItem("커플 기념티", Theme.HONEYMOON, 4);
		warehouseService.addItem("골프공", Theme.GOLF, 30);
		warehouseService.addItem("스카프", Theme.TREKKING, 12);

		assertThat(warehouseService.getItems()).extracting(Item::getName)
			.containsExactly("골프공", "스카프", "커플 기념티");
	}

	@Test
	void 여러_직원이_같은_물품을_동시에_더해도_수량을_잃지_않는다() throws Exception {
		warehouseService.addItem("생수", Theme.GOLF, 100);

		List<Throwable> errors = runConcurrently(Collections.nCopies(8,
				() -> warehouseService.addItem("생수", Theme.GOLF, 1)));

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getQuantity).containsExactly(108);
	}

	@Test
	void 두_직원이_같은_새_물품을_동시에_처음_넣어도_둘_다_더해진다() throws Exception {
		PauseAfterFindByName.pauseOnce();   // 둘 다 "없다"고 읽은 뒤에 저장한다

		List<Throwable> errors = runConcurrently(List.of(
				() -> warehouseService.addItem("생수", Theme.GOLF, 3),
				() -> warehouseService.addItem("생수", Theme.GOLF, 5)));

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getQuantity)
			.containsExactly(tuple("생수", 8));
	}

	@Test
	void 두_직원이_이름이_다른_새_물품을_동시에_넣으면_둘_다_저장된다() throws Exception {
		PauseAfterFindByName.pauseOnce();   // 둘 다 "없다"고 읽은 뒤에 저장한다
		// 두 이름 사이에 다른 테스트가 쓰는 이름이 없어야 MySQL이 같은 구간을 잠가 매번 부딪힌다
		// (지운 행도 정리되기 전까지는 구간을 나눈다)
		List<Throwable> errors = runConcurrently(List.of(
				() -> warehouseService.addItem("모자", Theme.GOLF, 3),
				() -> warehouseService.addItem("물병", Theme.TREKKING, 5)));

		assertThat(errors).containsOnlyNulls();
		assertThat(itemRepository.findAll()).extracting(Item::getName, Item::getQuantity)
			.containsExactlyInAnyOrder(tuple("모자", 3), tuple("물병", 5));
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
					throw new AssertionError("동시 추가가 30초 안에 끝나지 않았다", e);
				}
			}
			return errors;
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * pauseOnce()를 부르면 ItemRepository.findByName이 끝난 뒤 두 번째 스레드가 거기까지 올 때까지 한 번 기다린다.
	 * 두 요청이 모두 "같은 이름이 없다"고 읽은 다음에 저장하게 만들어, 드물게만 생기는 충돌을 매번 일으킨다.
	 * 한 번 모이면 풀려서, 되돌려진 쪽이 다시 할 때는 기다리지 않는다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class PauseAfterFindByName {

		static final AtomicReference<CyclicBarrier> BARRIER = new AtomicReference<>();

		static void pauseOnce() {
			BARRIER.set(new CyclicBarrier(2, () -> BARRIER.set(null)));
		}

		@Bean
		static BeanPostProcessor pauseAfterFindByName() {
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
								if (barrier != null && method.getName().equals("findByName")) {
									barrier.await(10, TimeUnit.SECONDS);
								}
								return result;
							});
				}
			};
		}

	}

}
