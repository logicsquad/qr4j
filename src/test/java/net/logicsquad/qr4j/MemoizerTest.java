package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests on {@link Memoizer}.
 *
 * @author paulh
 * @since 1.1
 */
public class MemoizerTest {
	@Test
	public void returnsComputedValue() {
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> "value-" + i);
		assertEquals("value-1", memoizer.get(1));
		assertEquals("value-2", memoizer.get(2));
		return;
	}

	@Test
	public void returnsSameInstanceForSameKey() {
		Memoizer<Integer, Object> memoizer = new Memoizer<>(i -> new Object());
		Object first = memoizer.get(1);
		assertSame(first, memoizer.get(1));
		assertSame(first, memoizer.get(1));
		assertNotSame(first, memoizer.get(2));
		return;
	}

	@Test
	public void computesOncePerKey() {
		AtomicInteger calls = new AtomicInteger();
		Memoizer<Integer, Integer> memoizer = new Memoizer<>(i -> {
			calls.incrementAndGet();
			return i * 2;
		});
		for (int i = 0; i < 10; i++)
			assertEquals(10, memoizer.get(5));
		assertEquals(1, calls.get());

		memoizer.get(6);
		assertEquals(2, calls.get());
		return;
	}

	/**
	 * <p>
	 * Concurrent callers racing on a key that is not yet cached must not each compute their own value: the first
	 * caller through takes the {@code pending} slot and the rest wait on it. This is the part of
	 * {@link Memoizer#get(Object)} that the library itself never exercises deterministically.
	 * </p>
	 *
	 * <p>
	 * The latch makes every thread arrive at {@code get()} at roughly the same moment, and the deliberately slow
	 * mapping function widens the window in which a second thread could sneak past.
	 * </p>
	 */
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	public void concurrentCallersShareOneComputation() throws Exception {
		final int threads = 16;
		AtomicInteger calls = new AtomicInteger();
		Memoizer<Integer, Object> memoizer = new Memoizer<>(i -> {
			calls.incrementAndGet();
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return new Object();
		});

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<Object>> futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
				ready.countDown();
				go.await();
				return memoizer.get(42);
			})).collect(Collectors.toList());

			assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start");
			go.countDown();

			Set<Object> results = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			for (Future<Object> future : futures)
				results.add(future.get(10, TimeUnit.SECONDS));

			assertEquals(1, results.size(), "callers received different instances");
			assertEquals(1, calls.get(), "mapping function ran more than once");
		} finally {
			pool.shutdownNow();
		}
		return;
	}

	/**
	 * Different keys are computed independently, and concurrent callers across a range of keys should each get a
	 * single distinct value per key.
	 */
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	public void concurrentCallersAcrossKeysGetOneValueEach() throws Exception {
		final int keys = 8;
		final int threadsPerKey = 4;
		AtomicInteger calls = new AtomicInteger();
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> {
			calls.incrementAndGet();
			return "value-" + i;
		});

		ExecutorService pool = Executors.newFixedThreadPool(keys * threadsPerKey);
		try {
			List<Future<String>> futures = IntStream.range(0, keys * threadsPerKey)
					.mapToObj(i -> pool.submit(() -> memoizer.get(i % keys))).collect(Collectors.toList());
			Set<String> results = new HashSet<>();
			for (Future<String> future : futures)
				results.add(future.get(10, TimeUnit.SECONDS));
			assertEquals(keys, results.size());
			assertEquals(keys, calls.get());
		} finally {
			pool.shutdownNow();
		}
		return;
	}

	/**
	 * A mapping function that throws must not leave the key stuck in the {@code pending} set, or every later
	 * caller for that key would block forever.
	 */
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	public void keyIsReusableAfterMappingFunctionThrows() {
		AtomicInteger calls = new AtomicInteger();
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> {
			if (calls.incrementAndGet() == 1)
				throw new IllegalStateException("boom");
			return "value-" + i;
		});

		try {
			memoizer.get(1);
		} catch (IllegalStateException expected) {
			// The first call is meant to fail.
		}
		assertEquals("value-1", memoizer.get(1));
		assertEquals(2, calls.get());
		return;
	}
}
