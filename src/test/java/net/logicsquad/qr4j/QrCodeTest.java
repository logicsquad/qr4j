package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.logicsquad.qr4j.QrCode.Ecc;
import net.logicsquad.qr4j.QrCode.Template;

/**
 * <p>
 * Unit tests on {@link QrCode}.
 * </p>
 *
 * <p>
 * The tests below check the precomputed lookup tables against the computed implementations they replaced. See
 * {@link QrCodeRoundTripTest} for verification of encoder output against an independent decoder, and
 * {@link QrCodeGoldenMasterTest} for characterisation of the rendered output.
 * </p>
 *
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 * @author paulh
 */
public class QrCodeTest {
	// Tests the lookup table against the original method
	@Test
	public void getNumDataCodewordsTest() {
		for (Ecc errorCorrectionLevel : Ecc.values()) {
			for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
				assertEquals(getNumDataCodewords(version, errorCorrectionLevel), QrCode.getNumDataCodewords(version, errorCorrectionLevel));
			}
		}
	}

	// Original method from QrCode
	private static int getNumDataCodewords(int version, Ecc errorCorrectionLevel) {
		return Template.getNumRawDataModules(version) / 8 - QrCode.ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version]
				* QrCode.NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
	}

	// Tests the lookup table against the original method
	@Test
	public void getAlignmentPatternPositionsTest() {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			assertArrayEquals(getAlignmentPatternPositions(version), QrCode.Template.getAlignmentPatternPositions(version));
		}
		return;
	}

	// Original method from QrCode
	private static int[] getAlignmentPatternPositions(int version) {
		if (version == 1)
			return new int[]{};
		else {
			int size = version * 4 + 17;
			int numAlign = version / 7 + 2;
			int step = (version == 32) ? 26 :
				(version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
			int[] result = new int[numAlign];
			result[0] = 6;
			for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}

	// Tests the lookup table against the original method
	@Test
	public void getNumRawDataModulesTest() {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			assertEquals(getNumRawDataModules(version), QrCode.Template.getNumRawDataModules(version));
		}
		return;
	}

	// Original method from QrCode
	private static int getNumRawDataModules(int version) {
		if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		int result = (16 * version + 128) * version + 64;
		if (version >= 2) {
			int numAlign = version / 7 + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (version >= 7)
				result -= 36;
		}
		return result;
	}

	// ---------------------------------------------------------------- constructor

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 41, Integer.MIN_VALUE, Integer.MAX_VALUE })
	public void constructorThrowsWhenVersionOutOfRange(int version) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new QrCode(version, Ecc.LOW, new byte[19], 0));
		assertEquals("Version value out of range", e.getMessage());
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { -2, 8, Integer.MIN_VALUE, Integer.MAX_VALUE })
	public void constructorThrowsWhenMaskOutOfRange(int mask) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new QrCode(1, Ecc.LOW, new byte[19], mask));
		assertEquals("Mask value out of range", e.getMessage());
		return;
	}

	@Test
	public void constructorThrowsOnNulls() {
		assertThrows(NullPointerException.class, () -> new QrCode(1, null, new byte[19], 0));
		assertThrows(NullPointerException.class, () -> new QrCode(1, Ecc.LOW, null, 0));
		return;
	}

	@Test
	public void constructorThrowsWhenDataIsWrongLengthForVersionAndLevel() {
		// Version 1 at level LOW takes exactly 19 data codewords.
		assertEquals(19, QrCode.getNumDataCodewords(1, Ecc.LOW));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new QrCode(1, Ecc.LOW, new byte[5], 0));
		assertEquals("Expected 19 data codewords for version 1 at level LOW, but got 5", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> new QrCode(1, Ecc.LOW, new byte[20], 0));
		return;
	}

	@Test
	public void constructorAcceptsCorrectlySizedData() {
		QrCode qr = new QrCode(1, Ecc.LOW, new byte[19], 0);
		assertEquals(1, qr.version);
		assertEquals(21, qr.size);
		assertEquals(Ecc.LOW, qr.errorCorrectionLevel);
		assertEquals(0, qr.mask);
		return;
	}

	/**
	 * A mask of -1 asks for automatic selection, and must resolve to a concrete mask in the range [0, 7].
	 */
	@Test
	public void constructorResolvesAutomaticMask() {
		QrCode qr = new QrCode(1, Ecc.LOW, new byte[19], -1);
		assertTrue(0 <= qr.mask && qr.mask <= 7, "mask was " + qr.mask);
		return;
	}

	// ---------------------------------------------------------------- encoding

	@Test
	public void encodeTextThrowsOnNulls() {
		assertThrows(NullPointerException.class, () -> QrCode.encodeText(null, Ecc.LOW));
		assertThrows(NullPointerException.class, () -> QrCode.encodeText("A", null));
		return;
	}

	@Test
	public void encodeBinaryThrowsOnNulls() {
		assertThrows(NullPointerException.class, () -> QrCode.encodeBinary(null, Ecc.LOW));
		assertThrows(NullPointerException.class, () -> QrCode.encodeBinary(new byte[1], null));
		return;
	}

	@Test
	public void encodeSegmentsThrowsOnNulls() {
		assertThrows(NullPointerException.class, () -> QrCode.encodeSegments(null, Ecc.LOW));
		assertThrows(NullPointerException.class, () -> QrCode.encodeSegments(QrSegment.makeSegments("A"), null));
		return;
	}

	@ParameterizedTest
	@CsvSource({ "0, 40, -1", "1, 41, -1", "5, 4, -1", "1, 40, -2", "1, 40, 8" })
	public void encodeSegmentsThrowsWhenParametersInvalid(int minVersion, int maxVersion, int mask) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> QrCode
				.encodeSegments(QrSegment.makeSegments("A"), Ecc.LOW, minVersion, maxVersion, mask, true));
		assertEquals("Invalid value", e.getMessage());
		return;
	}

	@Test
	public void encodeTextAcceptsEmptyString() {
		QrCode qr = QrCode.encodeText("", Ecc.LOW);
		assertEquals(1, qr.version);
		assertEquals(21, qr.size);
		return;
	}

	/**
	 * When the data length is known, the exception reports it against the available capacity.
	 */
	@Test
	public void encodeTextThrowsWhenDataTooLong() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 3000; i++)
			sb.append('A');
		DataTooLongException e = assertThrows(DataTooLongException.class, () -> QrCode.encodeText(sb.toString(), Ecc.HIGH));
		assertTrue(e.getMessage().startsWith("Data length = "), "unexpected message: " + e.getMessage());
		return;
	}

	/**
	 * When a segment's character count will not fit the count field, the data length cannot be computed at all,
	 * and the exception falls back to a message that does not quote one.
	 */
	@Test
	public void encodeSegmentsReportsSegmentTooLongWhenLengthCannotBeComputed() {
		// Byte mode at version 1 has an 8 bit character count field, so 256 bytes cannot be described.
		List<QrSegment> segments = Arrays.asList(QrSegment.makeBytes(new byte[256]));
		DataTooLongException e = assertThrows(DataTooLongException.class,
				() -> QrCode.encodeSegments(segments, Ecc.LOW, 1, 1, -1, false));
		assertEquals("Segment too long", e.getMessage());
		return;
	}

	/**
	 * With {@code boostEcl} set, the error correction level is raised as far as it can go without needing a larger
	 * version; without it, the requested level is used as-is.
	 */
	@Test
	public void encodeSegmentsBoostsErrorCorrectionLevelOnlyWhenAsked() {
		List<QrSegment> segments = QrSegment.makeSegments("HELLO");
		QrCode boosted = QrCode.encodeSegments(segments, Ecc.LOW, 1, 40, -1, true);
		QrCode plain = QrCode.encodeSegments(segments, Ecc.LOW, 1, 40, -1, false);
		assertEquals(Ecc.HIGH, boosted.errorCorrectionLevel);
		assertEquals(Ecc.LOW, plain.errorCorrectionLevel);
		// Boosting must not cost a larger symbol.
		assertEquals(plain.version, boosted.version);
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
	public void encodeSegmentsHonoursForcedMask(int mask) {
		QrCode qr = QrCode.encodeSegments(QrSegment.makeSegments("HELLO"), Ecc.LOW, 1, 40, mask, true);
		assertEquals(mask, qr.mask);
		return;
	}

	@Test
	public void encodeSegmentsRespectsMinimumVersion() {
		QrCode qr = QrCode.encodeSegments(QrSegment.makeSegments("A"), Ecc.LOW, 10, 40, -1, false);
		assertEquals(10, qr.version);
		assertEquals(10 * 4 + 17, qr.size);
		return;
	}

	// ---------------------------------------------------------------- getModule

	@Test
	public void getModuleReturnsFalseOutsideBounds() {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		assertFalse(qr.getModule(-1, 0));
		assertFalse(qr.getModule(0, -1));
		assertFalse(qr.getModule(qr.size, 0));
		assertFalse(qr.getModule(0, qr.size));
		assertFalse(qr.getModule(Integer.MIN_VALUE, Integer.MIN_VALUE));
		assertFalse(qr.getModule(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return;
	}

	/**
	 * The top left module of the top left finder pattern is always dark, and the module just outside its
	 * separator is always light.
	 */
	@Test
	public void getModuleReturnsFinderPatternModules() {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		assertTrue(qr.getModule(0, 0));
		assertTrue(qr.getModule(6, 6));
		assertFalse(qr.getModule(7, 7));
		return;
	}

	// ---------------------------------------------------------------- rendering guards

	@ParameterizedTest
	@CsvSource({ "0, 0", "-1, 0", "-100, 4", "1, -1", "10, -5" })
	public void toImageThrowsWhenScaleOrBorderOutOfRange(int scale, int border) {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> qr.toImage(scale, border));
		assertEquals("Value out of range", e.getMessage());
		return;
	}

	@ParameterizedTest
	@CsvSource({ "1, 2147483647", "2147483647, 0", "2147483647, 2147483647" })
	public void toImageThrowsWhenResultWouldBeTooLarge(int scale, int border) {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> qr.toImage(scale, border));
		assertEquals("Scale or border too large", e.getMessage());
		return;
	}

	@Test
	public void toSvgThrowsOnNullColours() {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		assertThrows(NullPointerException.class, () -> qr.toSvg(4, null, "#000000"));
		assertThrows(NullPointerException.class, () -> qr.toSvg(4, "#FFFFFF", null));
		assertThrows(NullPointerException.class, () -> qr.toSvg(4, null, "#000000", true));
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, -100, Integer.MIN_VALUE })
	public void toSvgThrowsWhenBorderNegative(int border) {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> qr.toSvg(border, "#FFFFFF", "#000000"));
		assertEquals("Border must be non-negative", e.getMessage());
		return;
	}

	@Test
	public void toSvgAcceptsZeroBorder() {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		assertTrue(qr.toSvg(0, "#FFFFFF", "#000000").contains("viewBox=\"0 0 21 21\""));
		return;
	}

	// ---------------------------------------------------------------- Ecc and Template

	@Test
	public void eccFormatBitsHaveSpecifiedValues() {
		assertEquals(1, Ecc.LOW.formatBits);
		assertEquals(0, Ecc.MEDIUM.formatBits);
		assertEquals(3, Ecc.QUARTILE.formatBits);
		assertEquals(2, Ecc.HIGH.formatBits);
		return;
	}

	/**
	 * {@link Ecc} is declared in ascending order of protection, which the ECC boosting loop relies on.
	 */
	@Test
	public void eccIsDeclaredInAscendingOrderOfProtection() {
		assertArrayEquals(new Ecc[] { Ecc.LOW, Ecc.MEDIUM, Ecc.QUARTILE, Ecc.HIGH }, Ecc.values());
		return;
	}

	@Test
	public void sizeIsDerivedFromVersion() {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			QrCode qr = new QrCode(version, Ecc.LOW, new byte[QrCode.getNumDataCodewords(version, Ecc.LOW)], 0);
			assertEquals(version * 4 + 17, qr.size);
		}
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 41, -1 })
	public void templateThrowsWhenVersionOutOfRange(int version) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Template.MEMOIZER.get(version));
		assertEquals("Version out of range", e.getMessage());
		return;
	}
}
