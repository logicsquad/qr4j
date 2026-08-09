package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.logicsquad.qr4j.QrCode.Ecc;

/**
 * <p>
 * Checks that encoding from several threads at once produces the same results as encoding one at a time.
 * </p>
 *
 * <p>
 * The library holds process-wide state — the {@link Memoizer}s behind {@code QrCode.Template} and
 * {@link ReedSolomonGenerator}, and the static lookup tables in {@link QrSegment} — and the README suggests
 * generating codes from a web application, so concurrent use is expected. These tests exist to catch a gross
 * regression in that area: a scratch buffer hoisted into a field, say, or a cached {@code Template} becoming
 * mutable.
 * </p>
 *
 * <p>
 * Be clear about what they do not do. Sampling a handful of interleavings does not prove thread safety, and a
 * green run here is evidence rather than proof. The complementary race — several callers arriving on a
 * <em>cold</em> memoizer key — is covered deterministically by
 * {@link MemoizerTest#concurrentCallersShareOneComputation()}, and cannot usefully be retested here, because by
 * the time this class runs the earlier tests have populated every template and generator.
 * </p>
 *
 * @author paulh
 * @since 1.1
 */
public class QrCodeConcurrencyTest {
	/**
	 * Number of worker threads.
	 */
	private static final int THREADS = 16;

	/**
	 * Number of times each worker repeats its workload.
	 */
	private static final int ITERATIONS = 25;

	/**
	 * Payloads covering each segment mode.
	 */
	private static final String[] TEXTS = {
		"Hello, world!",
		"314159265358979323846264338327950288419716939937510",
		"DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/",
		"https://logicsquad.net/",
		"こんにちwa、世界！ αβγδ",
	};

	/**
	 * Text encodable in kanji mode.
	 */
	private static final String MADOKA = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";

	/**
	 * Renders {@code qr} as a single string, for comparing whole symbols.
	 *
	 * @param qr a {@link QrCode}
	 * @return text rendering of the module grid
	 */
	private static String render(QrCode qr) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < qr.size; y++) {
			for (int x = 0; x < qr.size; x++)
				sb.append(qr.getModule(x, y) ? '#' : '.');
		}
		return sb.toString();
	}

	/**
	 * <p>
	 * Runs {@code workload} on {@link #THREADS} threads, released together so that they overlap as much as
	 * possible, and fails with the collected descriptions if any worker reports a discrepancy.
	 * </p>
	 *
	 * <p>
	 * Workers return a description of what went wrong rather than throwing, so that a failure is reported as a
	 * readable assertion rather than as an {@code ExecutionException} wrapping something from a pool thread.
	 * </p>
	 *
	 * @param workload work to perform, returning {@code null} on success or a description of the discrepancy
	 * @throws Exception if a worker cannot be run to completion
	 */
	private static void runConcurrently(Callable<String> workload) throws Exception {
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch go = new CountDownLatch(1);
		ConcurrentLinkedQueue<String> problems = new ConcurrentLinkedQueue<>();
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					for (int j = 0; j < ITERATIONS; j++) {
						String problem = workload.call();
						if (problem != null) {
							problems.add(problem);
							return null;
						}
					}
					return null;
				}));
			}
			assertTrue(ready.await(30, TimeUnit.SECONDS), "workers did not start");
			go.countDown();
			for (Future<?> future : futures)
				future.get(60, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}
		assertEquals(Collections.emptyList(), new ArrayList<>(problems));
		return;
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void encodeTextIsConsistentUnderConcurrentUse() throws Exception {
		Map<String, String> expected = new LinkedHashMap<>();
		for (String text : TEXTS)
			expected.put(text, render(QrCode.encodeText(text, Ecc.QUARTILE)));

		runConcurrently(() -> {
			for (Map.Entry<String, String> entry : expected.entrySet()) {
				String actual = render(QrCode.encodeText(entry.getKey(), Ecc.QUARTILE));
				if (!entry.getValue().equals(actual))
					return "encodeText produced a different symbol for \"" + entry.getKey() + "\"";
			}
			return null;
		});
		return;
	}

	/**
	 * Spreads work across versions and error correction levels, so that several cached {@code Template}s and
	 * several Reed-Solomon generators are in use at once rather than a single pair.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void encodeSegmentsIsConsistentAcrossVersionsAndLevels() throws Exception {
		int[] versions = { 1, 7, 14, 26, 27, 40 };
		Map<String, String> expected = new LinkedHashMap<>();
		for (int version : versions) {
			for (Ecc ecl : Ecc.values())
				expected.put(version + "/" + ecl, render(encode(version, ecl)));
		}

		runConcurrently(() -> {
			for (int version : versions) {
				for (Ecc ecl : Ecc.values()) {
					String key = version + "/" + ecl;
					if (!expected.get(key).equals(render(encode(version, ecl))))
						return "encodeSegments produced a different symbol at " + key;
				}
			}
			return null;
		});
		return;
	}

	/**
	 * Encodes a payload sized to fill the given version at the given error correction level.
	 *
	 * @param version version to force
	 * @param ecl     error correction level
	 * @return a {@link QrCode} of exactly {@code version}
	 */
	private static QrCode encode(int version, Ecc ecl) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < QrCode.getNumDataCodewords(version, ecl) - 2; i++)
			sb.append((char) ('0' + i % 10));
		return QrCode.encodeSegments(QrSegment.makeSegments(sb.toString()), ecl, version, version, -1, false);
	}

	/**
	 * The segment factories read the static alphanumeric and kanji lookup tables, and
	 * {@code makeSegmentsOptimally()} reads both.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void segmentFactoriesAreConsistentUnderConcurrentUse() throws Exception {
		String mixed = "THE SQUARE ROOT OF 2 IS 1.41421356237309504880168872420969807856967187537694807317667973799";
		int expectedNumeric = QrSegment.makeNumeric("314159").bitLength;
		int expectedAlphanumeric = QrSegment.makeAlphanumeric("AC-42").bitLength;
		int expectedBytes = QrSegment.makeBytes("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)).bitLength;
		int expectedKanji = QrSegment.Utility.makeKanji(MADOKA).bitLength;
		int expectedOptimal = QrSegment.getTotalBits(
				QrSegment.Utility.makeSegmentsOptimally(mixed, Ecc.LOW, QrCode.MIN_VERSION, QrCode.MAX_VERSION), 1);

		runConcurrently(() -> {
			if (QrSegment.makeNumeric("314159").bitLength != expectedNumeric)
				return "makeNumeric returned a different bit length";
			if (QrSegment.makeAlphanumeric("AC-42").bitLength != expectedAlphanumeric)
				return "makeAlphanumeric returned a different bit length";
			if (QrSegment.makeBytes("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)).bitLength != expectedBytes)
				return "makeBytes returned a different bit length";
			if (QrSegment.Utility.makeKanji(MADOKA).bitLength != expectedKanji)
				return "makeKanji returned a different bit length";
			int optimal = QrSegment.getTotalBits(
					QrSegment.Utility.makeSegmentsOptimally(mixed, Ecc.LOW, QrCode.MIN_VERSION, QrCode.MAX_VERSION), 1);
			if (optimal != expectedOptimal)
				return "makeSegmentsOptimally returned a different bit length";
			return null;
		});
		return;
	}

	/**
	 * Every thread should see the one cached instance per key, rather than each building its own. This is what
	 * makes the memoizers worth having, and it is also the property that would silently degrade if the caching
	 * were broken — output would stay correct while the work was repeated.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void memoizedInstancesAreSharedAcrossThreads() throws Exception {
		QrCode.Template template = QrCode.Template.MEMOIZER.get(5);
		ReedSolomonGenerator generator = ReedSolomonGenerator.MEMOIZER.get(10);

		runConcurrently(() -> {
			if (QrCode.Template.MEMOIZER.get(5) != template)
				return "Template.MEMOIZER returned a different instance for version 5";
			if (ReedSolomonGenerator.MEMOIZER.get(10) != generator)
				return "ReedSolomonGenerator.MEMOIZER returned a different instance for degree 10";
			return null;
		});

		assertSame(template, QrCode.Template.MEMOIZER.get(5));
		assertSame(generator, ReedSolomonGenerator.MEMOIZER.get(10));
		return;
	}

	/**
	 * Encoding the same segment list from several threads must not disturb the segments themselves, which are
	 * documented as immutable and are shared here by every worker.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void sharedSegmentsAreNotDisturbedByConcurrentEncoding() throws Exception {
		List<QrSegment> segments = Arrays.asList(QrSegment.makeAlphanumeric("THE SQUARE ROOT OF 2 IS 1."),
				QrSegment.makeNumeric("41421356237309504880168872420969807856967187537694807317667973799"));
		String expected = render(QrCode.encodeSegments(segments, Ecc.LOW));

		runConcurrently(() -> {
			if (!expected.equals(render(QrCode.encodeSegments(segments, Ecc.LOW))))
				return "shared segment list produced a different symbol";
			return null;
		});
		return;
	}
}
