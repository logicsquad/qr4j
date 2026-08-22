package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

	/**
	 * <p>
	 * Reaches the private cache so that a test can clear a {@link SoftReference} the way the collector would.
	 * </p>
	 *
	 * <p>
	 * The alternative — allocating until the JVM reclaims soft references of its own accord — is slow, sensitive to
	 * heap settings, and not reliably reproducible. Clearing a reference directly reproduces the same event
	 * deterministically. Note that this reaches a private <em>field</em> in order to trigger a runtime event, rather
	 * than reaching past a private method to test code that callers cannot otherwise reach.
	 * </p>
	 *
	 * @param memoizer a {@link Memoizer}
	 * @param <T>      index type
	 * @param <R>      return type
	 * @return the memoizer's cache
	 * @throws Exception if the field cannot be reached
	 */
	@SuppressWarnings("unchecked")
	private static <T, R> Map<T, SoftReference<R>> cacheOf(Memoizer<T, R> memoizer) throws Exception {
		Field field = Memoizer.class.getDeclaredField("cache");
		field.setAccessible(true);
		return (Map<T, SoftReference<R>>) field.get(memoizer);
	}

	/**
	 * <p>
	 * Caching by {@link SoftReference} is the entire premise of this class: it lets the collector reclaim a cached
	 * {@code Template} or {@code ReedSolomonGenerator} under memory pressure, leaving {@code get()} to rebuild it on
	 * demand. Nothing else in the suite ever clears a reference, so that rebuild has never run.
	 * </p>
	 *
	 * <p>
	 * A caller must see a correct value again, and the rebuilt value must go back into the cache rather than being
	 * recomputed on every later call — the latter would leave output correct while quietly discarding the caching
	 * this class exists to provide.
	 * </p>
	 */
	@Test
	public void clearedReferenceIsRecomputedAndRecached() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> {
			calls.incrementAndGet();
			return "value-" + i;
		});
		assertEquals("value-1", memoizer.get(1));
		assertEquals(1, calls.get());

		cacheOf(memoizer).get(1).clear();

		assertEquals("value-1", memoizer.get(1), "cleared reference was not rebuilt");
		assertEquals(2, calls.get(), "rebuild did not call the mapping function exactly once more");

		assertEquals("value-1", memoizer.get(1));
		assertEquals(2, calls.get(), "rebuilt value was not put back in the cache");
		return;
	}

	/**
	 * A cleared reference must be replaced in the cache rather than left sitting beside the rebuilt value.
	 * {@code get()} asserts as much for itself, which is worth something only if a test reaches that line — and
	 * Surefire runs with assertions enabled.
	 */
	@Test
	public void clearedReferenceLeavesNoStaleEntry() throws Exception {
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> "value-" + i);
		String first = memoizer.get(1);
		cacheOf(memoizer).get(1).clear();

		String second = memoizer.get(1);
		assertNotSame(first, second, "expected a freshly computed value after clearing");

		SoftReference<String> cached = cacheOf(memoizer).get(1);
		assertNotNull(cached, "key is missing from the cache after rebuild");
		assertSame(second, cached.get(), "cache does not hold the rebuilt value");
		return;
	}

	/**
	 * <p>
	 * A caller arriving on a key that another thread is already computing waits on the monitor. Interrupting it
	 * there is the last path through {@link Memoizer#get(Object)} that no test reaches.
	 * </p>
	 *
	 * <p>
	 * Be clear about what the last assertion pins. The interrupt is wrapped in a {@link RuntimeException} and the
	 * thread's interrupt status is <em>not</em> restored, so an executor shutting down upstream will not see it.
	 * That is a defect rather than a decision, and asserting it here records the current behaviour so that changing
	 * it has to be deliberate rather than accidental.
	 * </p>
	 */
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	public void interruptingWaiterOnPendingKeyIsWrappedAndLosesInterruptStatus() throws Exception {
		CountDownLatch computing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Memoizer<Integer, String> memoizer = new Memoizer<>(i -> {
			computing.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return "value-" + i;
		});

		// Once the mapping function has started, the key is certainly in the pending set.
		Thread holder = new Thread(() -> memoizer.get(1), "holder");
		holder.start();
		assertTrue(computing.await(10, TimeUnit.SECONDS), "mapping function did not start");

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		AtomicReference<Boolean> stillInterrupted = new AtomicReference<>();
		Thread waiter = new Thread(() -> {
			try {
				memoizer.get(1);
			} catch (Throwable t) {
				thrown.set(t);
				stillInterrupted.set(Thread.currentThread().isInterrupted());
			}
		}, "waiter");
		waiter.start();

		assertTrue(awaitState(waiter, Thread.State.WAITING), "waiter never blocked on the pending key");
		waiter.interrupt();
		waiter.join(TimeUnit.SECONDS.toMillis(10));

		assertNotNull(thrown.get(), "waiter returned a value instead of being interrupted");
		assertEquals(RuntimeException.class, thrown.get().getClass());
		assertEquals(InterruptedException.class, thrown.get().getCause().getClass());
		assertFalse(stillInterrupted.get(),
				"interrupt status is now restored, so Memoizer.get() has been fixed and this test should change");

		// The interrupted waiter must not have disturbed the computation it was waiting on.
		release.countDown();
		holder.join(TimeUnit.SECONDS.toMillis(10));
		assertEquals("value-1", memoizer.get(1), "the holder's computation did not survive the interrupt");
		return;
	}

	/**
	 * Waits for {@code thread} to reach {@code state}, so that a test need not guess at a sleep long enough for a
	 * loaded CI runner.
	 *
	 * @param thread thread to watch
	 * @param state  state to wait for
	 * @return {@code true} if {@code thread} reached {@code state} before the timeout
	 * @throws InterruptedException if the calling thread is interrupted while waiting
	 */
	private static boolean awaitState(Thread thread, Thread.State state) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (thread.getState() == state)
				return true;
			Thread.sleep(5);
		}
		return false;
	}
}
