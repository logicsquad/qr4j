package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.logicsquad.qr4j.QrSegment.Mode;

/**
 * Unit tests on {@link QrSegment.Utility}.
 *
 * @author paulh
 * @since 1.1
 */
public class QrSegmentUtilityTest {
	/**
	 * Returns a description of the {@link Mode}s and character counts of {@code segments}, for use in assertions.
	 *
	 * @param segments list of {@link QrSegment}s
	 * @return description in the form {@code "MODE(count) MODE(count)"}
	 */
	private static String describe(List<QrSegment> segments) {
		StringBuilder sb = new StringBuilder();
		for (QrSegment segment : segments) {
			if (sb.length() > 0)
				sb.append(' ');
			sb.append(segment.mode).append('(').append(segment.numChars).append(')');
		}
		return sb.toString();
	}

	/**
	 * Regression test: {@code computeCharacterModes()} indexed {@code ALPHANUMERIC_MAP} without a bounds check,
	 * so any code point beyond the end of that table (i.e. anything above {@code 'Z'}, which includes every
	 * lowercase letter and every non-ASCII character) threw {@link ArrayIndexOutOfBoundsException}.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "hello", "Hello, world!", "abc", "é", "一", "Golden ratio φ = 1.6180339887",
			"「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？" })
	public void makeSegmentsOptimallyAcceptsCodePointsAboveAlphanumericTable(String text) {
		assertDoesNotThrow(() -> QrSegment.Utility.makeSegmentsOptimally(text, QrCode.Ecc.LOW, 1, 40));
		return;
	}

	@Test
	public void makeSegmentsOptimallySelectsExpectedModes() {
		assertEquals("", describe(QrSegment.Utility.makeSegmentsOptimally("", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("NUMERIC(5)", describe(QrSegment.Utility.makeSegmentsOptimally("12345", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("ALPHANUMERIC(6)", describe(QrSegment.Utility.makeSegmentsOptimally("ABC123", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("BYTE(5)", describe(QrSegment.Utility.makeSegmentsOptimally("hello", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("KANJI(1)", describe(QrSegment.Utility.makeSegmentsOptimally("一", QrCode.Ecc.LOW, 1, 40)));
		return;
	}

	/**
	 * {@code numChars} for a byte mode segment counts UTF-8 bytes, not characters.
	 */
	@Test
	public void makeSegmentsOptimallyCountsUtf8BytesForByteMode() {
		assertEquals("BYTE(2)", describe(QrSegment.Utility.makeSegmentsOptimally("é", QrCode.Ecc.LOW, 1, 40)));
		return;
	}

	@Test
	public void makeSegmentsOptimallySwitchesModesWithinText() {
		assertEquals("BYTE(3) ALPHANUMERIC(6)",
				describe(QrSegment.Utility.makeSegmentsOptimally("abc123XYZ", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("BYTE(20) NUMERIC(10)",
				describe(QrSegment.Utility.makeSegmentsOptimally("Golden ratio φ = 1.6180339887", QrCode.Ecc.LOW, 1, 40)));
		return;
	}

	/**
	 * The whole point of {@link QrSegment.Utility#makeSegmentsOptimally(String, QrCode.Ecc, int, int)} is that it
	 * should beat {@link QrSegment#makeSegments(String)} on mixed-mode text.
	 */
	@Test
	public void makeSegmentsOptimallyBeatsMakeSegmentsOnMixedText() {
		String text = "THE SQUARE ROOT OF 2 IS 1.41421356237309504880168872420969807856967187537694807317667973799";
		int naive = QrSegment.getTotalBits(QrSegment.makeSegments(text), 1);
		int optimal = QrSegment.getTotalBits(QrSegment.Utility.makeSegmentsOptimally(text, QrCode.Ecc.LOW, 1, 40), 1);
		assertEquals(514, naive);
		assertEquals(387, optimal);
		assertTrue(optimal < naive);
		return;
	}

	/**
	 * Multi-byte characters are measured in UTF-8 bytes when they land in byte mode.
	 */
	@Test
	public void makeSegmentsOptimallyCountsMultiByteCharacters() {
		assertEquals("BYTE(3)", describe(QrSegment.Utility.makeSegmentsOptimally("€", QrCode.Ecc.LOW, 1, 40)));
		assertEquals("BYTE(4)", describe(QrSegment.Utility.makeSegmentsOptimally("😀", QrCode.Ecc.LOW, 1, 40)));
		return;
	}

	@Test
	public void makeSegmentsOptimallyThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.Utility.makeSegmentsOptimally(null, QrCode.Ecc.LOW, 1, 40));
		assertThrows(NullPointerException.class, () -> QrSegment.Utility.makeSegmentsOptimally("A", null, 1, 40));
		return;
	}

	@ParameterizedTest
	@CsvSource({ "0, 40", "1, 41", "5, 4", "-1, 40", "1, 100" })
	public void makeSegmentsOptimallyThrowsWhenVersionRangeInvalid(int minVersion, int maxVersion) {
		assertThrows(IllegalArgumentException.class,
				() -> QrSegment.Utility.makeSegmentsOptimally("A", QrCode.Ecc.LOW, minVersion, maxVersion));
		return;
	}

	/**
	 * An unpaired surrogate is not valid UTF-16 and cannot be converted to a code point.
	 */
	@Test
	public void makeSegmentsOptimallyRejectsUnpairedSurrogate() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> QrSegment.Utility.makeSegmentsOptimally("\uD800", QrCode.Ecc.LOW, 1, 40));
		assertEquals("Invalid UTF-16 string", e.getMessage());
		return;
	}

	/**
	 * A correctly paired surrogate is a single code point above the basic multilingual plane, and is fine.
	 */
	@Test
	public void makeSegmentsOptimallyAcceptsPairedSurrogate() {
		assertEquals("BYTE(4)", describe(QrSegment.Utility.makeSegmentsOptimally("😀", QrCode.Ecc.LOW, 1, 40)));
		return;
	}

	/**
	 * With more characters than the count field can describe, the length is unknown and the message says only
	 * that the segment is too long.
	 */
	@Test
	public void makeSegmentsOptimallyThrowsWhenSegmentLengthCannotBeComputed() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 600; i++)
			sb.append('a');
		DataTooLongException e = assertThrows(DataTooLongException.class,
				() -> QrSegment.Utility.makeSegmentsOptimally(sb.toString(), QrCode.Ecc.HIGH, 1, 2));
		assertEquals("Segment too long", e.getMessage());
		return;
	}

	/**
	 * When the length is computable but simply too big for the version range, the message quotes it against the
	 * available capacity.
	 */
	@Test
	public void makeSegmentsOptimallyReportsLengthAgainstCapacity() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 100; i++)
			sb.append('a');
		DataTooLongException e = assertThrows(DataTooLongException.class,
				() -> QrSegment.Utility.makeSegmentsOptimally(sb.toString(), QrCode.Ecc.HIGH, 1, 1));
		assertTrue(e.getMessage().startsWith("Data length = "), "unexpected message: " + e.getMessage());
		return;
	}

	@Test
	public void makeKanjiThrowsOnIllegalArgument() {
		assertThrows(NullPointerException.class, () -> QrSegment.Utility.makeKanji(null));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.Utility.makeKanji("abc"));
		return;
	}

	/**
	 * Kanji mode encodes each character in 13 bits.
	 */
	@Test
	public void makeKanjiUsesThirteenBitsPerCharacter() {
		QrSegment segment = QrSegment.Utility.makeKanji("一");
		assertEquals(Mode.KANJI, segment.mode);
		assertEquals(1, segment.numChars);
		assertEquals(13, segment.bitLength);
		assertEquals(13 * 13, QrSegment.Utility.makeKanji("「魔法少女まどか☆マギカ」").bitLength);
		return;
	}

	@Test
	public void isEncodableAsKanjiThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.Utility.isEncodableAsKanji(null));
		return;
	}

	@Test
	public void isEncodableAsKanjiReturnsExpectedResult() {
		assertTrue(QrSegment.Utility.isEncodableAsKanji("一"));
		assertTrue(QrSegment.Utility.isEncodableAsKanji("「魔法少女まどか☆マギカ」"));
		assertTrue(QrSegment.Utility.isEncodableAsKanji(""));
		assertFalse(QrSegment.Utility.isEncodableAsKanji("abc"));
		assertFalse(QrSegment.Utility.isEncodableAsKanji("Hello, world!"));
		return;
	}

	@Test
	public void utilityCannotBeInstantiated() throws Exception {
		Constructor<QrSegment.Utility> constructor = QrSegment.Utility.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> constructor.newInstance());
		assertTrue(e.getCause() instanceof AssertionError);
		return;
	}
}
