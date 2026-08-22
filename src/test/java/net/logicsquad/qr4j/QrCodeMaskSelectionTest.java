package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.logicsquad.qr4j.QrCode.Ecc;

/**
 * <p>
 * Pins the mask chosen by automatic mask selection for a spread of payloads and error correction levels.
 * </p>
 *
 * <p>
 * Automatic selection works by scoring all eight candidate masks with {@code getPenaltyScore()} and taking the
 * lowest, so the chosen mask is the only externally visible consequence of that scoring — the score itself is
 * never exposed. Mutation testing showed the penalty logic to be the weakest spot in the library by a wide
 * margin: of 101 mutants across {@code getPenaltyScore()} and its three helpers, only 23 were killed, despite
 * PIT running 55 to 68 tests against each one. A mutation that shifts a score without changing which mask wins
 * goes unnoticed.
 * </p>
 *
 * <p>
 * The table below is a wider net than the eight golden master fixtures: sixteen payloads across four error
 * correction levels, between them selecting every one of the eight masks. It cannot catch a scoring change that
 * leaves every argmin intact — that would need {@code getPenaltyScore()} itself to be visible — but it does
 * catch anything that changes an outcome.
 * </p>
 *
 * @author paulh
 * @since 1.1
 */
public class QrCodeMaskSelectionTest {
	/**
	 * Payload, then the mask expected at each error correction level in {@link Ecc} declaration order: LOW,
	 * MEDIUM, QUARTILE, HIGH.
	 */
	private static final Object[][] EXPECTED_MASKS = {
		{ "", 6, 6, 6, 6, },
		{ "A", 4, 4, 4, 4, },
		{ "1", 0, 0, 0, 0, },
		{ "HELLO", 3, 3, 3, 3, },
		{ "Hello, world!", 2, 2, 2, 2, },
		{ "https://logicsquad.net/", 6, 6, 2, 2, },
		{ "314159265358979323846264338327950288419716939937510", 3, 3, 6, 6, },
		{ "DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", 1, 1, 0, 1, },
		{ "こんにちwa、世界！ αβγδ", 7, 7, 6, 6, },
		{ "維基百科", 2, 2, 6, 6, },
		{ "THE QUICK BROWN FOX", 6, 6, 1, 1, },
		{ "0000000000000000000000000000000000000000", 0, 0, 0, 0, },
		{ "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 6, 2, 2, 5, },
		{ "9876543210", 3, 3, 3, 3, },
		{ "abcdefghijklmnopqrstuvwxyz", 3, 3, 6, 3, },
		{ "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs.", 6, 0, 2, 6, },
	};

	private static Stream<Arguments> maskSelection() {
		return Stream.of(EXPECTED_MASKS).flatMap(row -> IntStream.range(0, Ecc.values().length)
				.mapToObj(i -> Arguments.of(row[0], Ecc.values()[i], row[i + 1])));
	}

	@ParameterizedTest(name = "{1} \"{0}\"")
	@MethodSource("maskSelection")
	public void automaticMaskSelectionChoosesExpectedMask(String text, Ecc ecl, int expectedMask) {
		assertEquals(expectedMask, QrCode.encodeText(text, ecl).mask);
		return;
	}

	/**
	 * The table is only a useful net if it actually reaches every mask, so assert that rather than trusting it to
	 * stay that way as payloads are edited.
	 */
	@Test
	public void tableExercisesEveryMask() {
		Set<Object> masks = new HashSet<>();
		for (Object[] row : EXPECTED_MASKS)
			masks.addAll(Arrays.asList(row).subList(1, row.length));
		assertEquals(8, masks.size(), "table no longer covers all eight masks: " + masks);
		return;
	}

	/**
	 * Whatever the scoring produces, the result must always be a legal mask.
	 */
	@ParameterizedTest(name = "{1} \"{0}\"")
	@MethodSource("maskSelection")
	public void automaticMaskSelectionAlwaysYieldsLegalMask(String text, Ecc ecl, int expectedMask) {
		int mask = QrCode.encodeText(text, ecl).mask;
		assertTrue(0 <= mask && mask <= 7, "mask out of range: " + mask);
		return;
	}

	/**
	 * Automatic selection should not be arbitrary: asking for the mask it chose must produce exactly the same
	 * symbol as letting it choose.
	 */
	@ParameterizedTest(name = "{1} \"{0}\"")
	@MethodSource("maskSelection")
	public void forcingTheChosenMaskReproducesTheSameSymbol(String text, Ecc ecl, int expectedMask) {
		QrCode automatic = QrCode.encodeText(text, ecl);
		QrCode forced = QrCode.encodeSegments(QrSegment.makeSegments(text), ecl, QrCode.MIN_VERSION,
				QrCode.MAX_VERSION, automatic.mask, true);
		assertEquals(automatic.size, forced.size);
		for (int y = 0; y < automatic.size; y++) {
			for (int x = 0; x < automatic.size; x++)
				assertEquals(automatic.getModule(x, y), forced.getModule(x, y), "module (" + x + ", " + y + ")");
		}
		return;
	}
}
