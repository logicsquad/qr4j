package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.logicsquad.qr4j.QrSegment.Mode;

/**
 * Unit tests on {@link QrSegment}.
 *
 * @author paulh
 * @since 1.0
 */
public class QrSegmentTest {
	/**
	 * Returns the data bits of {@code segment} as a string of {@code '0'} and {@code '1'} characters, so that
	 * encodings can be asserted directly.
	 *
	 * @param segment a {@link QrSegment}
	 * @return data bits, most significant first
	 */
	private static String bits(QrSegment segment) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < segment.bitLength; i++)
			sb.append((segment.data[i >>> 5] >>> (~i & 31)) & 1);
		return sb.toString();
	}

	@Test
	public void constructorThrowsExpectedExceptions() {
		assertThrows(NullPointerException.class, () -> new QrSegment(null, 0, new int[64], 8));
		assertThrows(NullPointerException.class, () -> new QrSegment(Mode.ALPHANUMERIC, 0, null, 8));
		assertThrows(NullPointerException.class, () -> new QrSegment(null, 0, null, 8));

		assertThrows(IllegalArgumentException.class, () -> new QrSegment(Mode.ALPHANUMERIC, -1, new int[64], 0));
		assertThrows(IllegalArgumentException.class, () -> new QrSegment(Mode.ALPHANUMERIC, 0, new int[64], -1));
		assertThrows(IllegalArgumentException.class, () -> new QrSegment(Mode.ALPHANUMERIC, 0, new int[64], 64 * 32 + 1));
		return;
	}

	@Test
	public void constructorRetainsSuppliedValues() {
		int[] data = new int[2];
		QrSegment segment = new QrSegment(Mode.BYTE, 3, data, 24);
		assertEquals(Mode.BYTE, segment.mode);
		assertEquals(3, segment.numChars);
		assertEquals(24, segment.bitLength);
		return;
	}

	/**
	 * The class documents itself as immutable and the constructor documents that it clones the supplied buffer, so
	 * a caller that keeps hold of the array must not be able to change the segment afterwards.
	 */
	@Test
	public void constructorClonesSuppliedData() {
		int[] data = new int[2];
		data[0] = 0x41420000;
		QrSegment segment = new QrSegment(Mode.BYTE, 2, data, 16);
		assertEquals("0100000101000010", bits(segment));

		data[0] = 0x58590000;
		assertEquals("0100000101000010", bits(segment), "mutating the caller's array changed the segment");
		return;
	}

	/**
	 * The same thing observed end to end: one segment must always encode to the same symbol.
	 */
	@Test
	public void mutatingCallersArrayDoesNotChangeEncodedOutput() {
		int[] data = new int[2];
		data[0] = 0x41420000;
		QrSegment segment = new QrSegment(Mode.BYTE, 2, data, 16);
		String before = render(QrCode.encodeSegments(Arrays.asList(segment), QrCode.Ecc.LOW));

		data[0] = 0x58590000;
		String after = render(QrCode.encodeSegments(Arrays.asList(segment), QrCode.Ecc.LOW));
		assertEquals(before, after);
		return;
	}

	/**
	 * The internal path deliberately does not clone, since the factory methods own the buffers they pass. Check
	 * that it still produces an equivalent segment.
	 */
	@Test
	public void ofOwnedDataRetainsSuppliedArray() {
		int[] data = new int[2];
		data[0] = 0x41420000;
		QrSegment segment = QrSegment.ofOwnedData(Mode.BYTE, 2, data, 16);
		assertSame(data, segment.data);
		assertEquals("0100000101000010", bits(segment));
		return;
	}

	@Test
	public void ofOwnedDataAppliesSameValidationAsConstructor() {
		assertThrows(NullPointerException.class, () -> QrSegment.ofOwnedData(null, 0, new int[64], 8));
		assertThrows(NullPointerException.class, () -> QrSegment.ofOwnedData(Mode.ALPHANUMERIC, 0, null, 8));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.ofOwnedData(Mode.ALPHANUMERIC, -1, new int[64], 0));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.ofOwnedData(Mode.ALPHANUMERIC, 0, new int[64], -1));
		assertThrows(IllegalArgumentException.class,
				() -> QrSegment.ofOwnedData(Mode.ALPHANUMERIC, 0, new int[64], 64 * 32 + 1));
		return;
	}

	/**
	 * Renders a {@link QrCode} as text, for comparing two symbols.
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

	@Test
	public void isAlphanumericThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.isAlphanumeric(null));
		return;
	}

	@Test
	public void isAlphanumericReturnsTrueWhenExpected() {
		assertTrue(QrSegment.isAlphanumeric("A"));
		assertTrue(QrSegment.isAlphanumeric("FOO BAR"));
		assertTrue(QrSegment.isAlphanumeric("A12 B178"));
		assertTrue(QrSegment.isAlphanumeric("11 2234"));
		assertTrue(QrSegment.isAlphanumeric("$%*+-./:"));
		assertTrue(QrSegment.isAlphanumeric("$A"));
		assertTrue(QrSegment.isAlphanumeric("$1$.$5%"));
		assertTrue(QrSegment.isAlphanumeric(""));
		return;
	}

	@Test
	public void isAlphanumericReturnsFalseWhenExpected() {
		assertFalse(QrSegment.isAlphanumeric(";"));
		assertFalse(QrSegment.isAlphanumeric("FOO\nBAR"));
		assertFalse(QrSegment.isAlphanumeric("FOO\tBAR"));
		assertFalse(QrSegment.isAlphanumeric("(FOO) "));
		assertFalse(QrSegment.isAlphanumeric("foobar"));
		return;
	}

	/**
	 * Characters beyond the end of the internal lookup table must be rejected rather than causing an
	 * {@link ArrayIndexOutOfBoundsException}.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "a", "é", "一", "￿" })
	public void isAlphanumericHandlesCodePointsBeyondLookupTable(String text) {
		assertFalse(QrSegment.isAlphanumeric(text));
		return;
	}

	@Test
	public void isNumericThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.isNumeric(null));
		return;
	}

	@Test
	public void isNumericReturnsTrueWhenExpected() {
		assertTrue(QrSegment.isNumeric("1"));
		assertTrue(QrSegment.isNumeric("0"));
		assertTrue(QrSegment.isNumeric("1234567890"));
		assertTrue(QrSegment.isNumeric(""));
		return;
	}

	@Test
	public void isNumericReturnsFalseWhenExpected() {
		assertFalse(QrSegment.isNumeric("-1"));
		assertFalse(QrSegment.isNumeric("1\n2"));
		assertFalse(QrSegment.isNumeric("FOOBAR"));
		assertFalse(QrSegment.isNumeric("1 2 "));
		return;
	}

	@Test
	public void makeAlphanumericThrowsOnIllegalArgument() {
		assertThrows(NullPointerException.class, () -> QrSegment.makeAlphanumeric(null));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeAlphanumeric("foo"));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeAlphanumeric("一"));
		return;
	}

	/**
	 * Alphanumeric mode packs two characters into 11 bits as {@code 45 * first + second}, and encodes a trailing
	 * odd character in 6 bits.
	 */
	@Test
	public void makeAlphanumericEncodesExpectedBits() {
		// 'A' is 10, 'B' is 11: 45 * 10 + 11 = 461
		assertEquals("00111001101", bits(QrSegment.makeAlphanumeric("AB")));
		assertEquals("001010", bits(QrSegment.makeAlphanumeric("A")));
		assertEquals("0011100111011100111001000010", bits(QrSegment.makeAlphanumeric("AC-42")));
		assertEquals("", bits(QrSegment.makeAlphanumeric("")));
		return;
	}

	@Test
	public void makeAlphanumericSetsModeAndCharacterCount() {
		QrSegment segment = QrSegment.makeAlphanumeric("F12 LK87");
		assertEquals(Mode.ALPHANUMERIC, segment.mode);
		assertEquals(8, segment.numChars);
		return;
	}

	@Test
	public void makeNumericThrowsOnIllegalArgument() {
		assertThrows(NullPointerException.class, () -> QrSegment.makeNumeric(null));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeNumeric("foo"));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeNumeric("12A"));
		return;
	}

	/**
	 * Numeric mode packs three digits into 10 bits, with a two digit remainder taking 7 bits and a one digit
	 * remainder taking 4.
	 */
	@Test
	public void makeNumericEncodesExpectedBits() {
		assertEquals("0100111010", bits(QrSegment.makeNumeric("314")));
		assertEquals("0011111", bits(QrSegment.makeNumeric("31")));
		assertEquals("0011", bits(QrSegment.makeNumeric("3")));
		assertEquals("01001110100010011111", bits(QrSegment.makeNumeric("314159")));
		assertEquals("", bits(QrSegment.makeNumeric("")));
		return;
	}

	@Test
	public void makeNumericSetsModeAndCharacterCount() {
		QrSegment segment = QrSegment.makeNumeric("1234567890");
		assertEquals(Mode.NUMERIC, segment.mode);
		assertEquals(10, segment.numChars);
		return;
	}

	@Test
	public void makeBytesThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.makeBytes(null));
		return;
	}

	@Test
	public void makeBytesEncodesExpectedBits() {
		assertEquals("0100000101000010", bits(QrSegment.makeBytes(new byte[] { 0x41, 0x42 })));
		assertEquals("11111111", bits(QrSegment.makeBytes(new byte[] { (byte) 0xFF })));
		assertEquals("", bits(QrSegment.makeBytes(new byte[0])));
		return;
	}

	@Test
	public void makeBytesSetsModeAndCharacterCount() {
		QrSegment segment = QrSegment.makeBytes(new byte[] { 0x41, 0x42, 0x43 });
		assertEquals(Mode.BYTE, segment.mode);
		assertEquals(3, segment.numChars);
		assertEquals(24, segment.bitLength);
		return;
	}

	@Test
	public void makeSegmentsThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.makeSegments(null));
		return;
	}

	@Test
	public void makeSegmentsChoosesNarrowestApplicableMode() {
		assertTrue(QrSegment.makeSegments("").isEmpty());
		assertEquals(Mode.NUMERIC, QrSegment.makeSegments("123").get(0).mode);
		assertEquals(Mode.ALPHANUMERIC, QrSegment.makeSegments("ABC").get(0).mode);
		assertEquals(Mode.ALPHANUMERIC, QrSegment.makeSegments("AB1$%*+-./:").get(0).mode);
		assertEquals(Mode.BYTE, QrSegment.makeSegments("abc").get(0).mode);
		return;
	}

	@Test
	public void makeSegmentsReturnsMutableList() {
		List<QrSegment> segments = QrSegment.makeSegments("123");
		segments.add(QrSegment.makeNumeric("4"));
		assertEquals(2, segments.size());
		return;
	}

	/**
	 * ECI assignment values are encoded in 8, 16 or 24 bits depending on magnitude.
	 */
	@Test
	public void makeEciEncodesExpectedBits() {
		assertEquals("00011010", bits(QrSegment.makeEci(26)));
		assertEquals("1000000010000000", bits(QrSegment.makeEci(128)));
		assertEquals("110000000100000000000000", bits(QrSegment.makeEci(16384)));
		return;
	}

	@ParameterizedTest
	@CsvSource({ "0, 8", "127, 8", "128, 16", "16383, 16", "16384, 24", "999999, 24" })
	public void makeEciUsesExpectedWidthAtBoundaries(int assignVal, int expectedBitLength) {
		QrSegment segment = QrSegment.makeEci(assignVal);
		assertEquals(Mode.ECI, segment.mode);
		assertEquals(0, segment.numChars);
		assertEquals(expectedBitLength, segment.bitLength);
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, -1000, 1000000, Integer.MAX_VALUE })
	public void makeEciThrowsWhenOutOfRange(int assignVal) {
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeEci(assignVal));
		return;
	}

	@Test
	public void getTotalBitsThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> QrSegment.getTotalBits(null, 1));
		assertThrows(NullPointerException.class, () -> QrSegment.getTotalBits(Collections.singletonList(null), 1));
		return;
	}

	/**
	 * Total bits is the sum over segments of the four mode bits, the character count field for the version, and
	 * the segment's own data bits.
	 */
	@Test
	public void getTotalBitsSumsHeadersAndData() {
		assertEquals(0, QrSegment.getTotalBits(new ArrayList<>(), 1));
		// 4 mode bits + 10 character count bits at version 1 + 10 data bits
		assertEquals(24, QrSegment.getTotalBits(Arrays.asList(QrSegment.makeNumeric("314")), 1));
		return;
	}

	/**
	 * A segment whose character count will not fit the count field for the given version cannot be encoded, which
	 * {@code getTotalBits()} reports as -1 rather than by throwing.
	 */
	@Test
	public void getTotalBitsReturnsMinusOneWhenCharacterCountFieldOverflows() {
		// Byte mode at version 1 has an 8 bit character count field, so 256 characters will not fit.
		QrSegment segment = QrSegment.makeBytes(new byte[256]);
		assertEquals(-1, QrSegment.getTotalBits(Arrays.asList(segment), 1));
		assertNotEquals(-1, QrSegment.getTotalBits(Arrays.asList(segment), 10));
		return;
	}

	/**
	 * The running total is accumulated as a {@code long} and reported as -1 rather than being allowed to wrap. One
	 * segment is shared across the list, so this costs a single 4MB buffer rather than 64 of them.
	 */
	@Test
	public void getTotalBitsReturnsMinusOneOnIntegerOverflow() {
		QrSegment large = new QrSegment(Mode.BYTE, 1, new int[1 << 20], 1 << 25);
		// 64 * (4 mode bits + 8 count bits + 2^25 data bits) exceeds Integer.MAX_VALUE
		assertEquals(-1, QrSegment.getTotalBits(Collections.nCopies(64, large), 1));
		assertNotEquals(-1, QrSegment.getTotalBits(Collections.nCopies(63, large), 1));
		return;
	}

	/**
	 * The character count field width changes at versions 10 and 27.
	 */
	@ParameterizedTest
	@CsvSource({ "1, 10, 9, 8, 8", "9, 10, 9, 8, 8", "10, 12, 11, 16, 10", "26, 12, 11, 16, 10", "27, 14, 13, 16, 12",
			"40, 14, 13, 16, 12" })
	public void numCharCountBitsChangesAtVersionBoundaries(int version, int numeric, int alphanumeric, int byteMode,
			int kanji) {
		assertEquals(numeric, Mode.NUMERIC.numCharCountBits(version));
		assertEquals(alphanumeric, Mode.ALPHANUMERIC.numCharCountBits(version));
		assertEquals(byteMode, Mode.BYTE.numCharCountBits(version));
		assertEquals(kanji, Mode.KANJI.numCharCountBits(version));
		assertEquals(0, Mode.ECI.numCharCountBits(version));
		return;
	}

	@Test
	public void modeBitsHaveSpecifiedValues() {
		assertEquals(0x1, Mode.NUMERIC.modeBits);
		assertEquals(0x2, Mode.ALPHANUMERIC.modeBits);
		assertEquals(0x4, Mode.BYTE.modeBits);
		assertEquals(0x8, Mode.KANJI.modeBits);
		assertEquals(0x7, Mode.ECI.modeBits);
		return;
	}
}
