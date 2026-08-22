package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests on {@link ReedSolomonGenerator}.
 *
 * @author paulh
 * @since 1.0
 */
public class ReedSolomonGeneratorTest {
	/**
	 * Degrees actually used by QR Codes, drawn from {@code QrCode.ECC_CODEWORDS_PER_BLOCK}, plus the extremes of
	 * the permitted range.
	 */
	private static final int[] DEGREES = { 1, 7, 10, 13, 15, 16, 17, 18, 20, 22, 24, 26, 28, 30, 255 };

	// Tests the lookup table against the original method
	@Test
	public void multiplyTest() {
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				assertEquals(multiply(x, y), ReedSolomonGenerator.multiply(x, y));
			}
		}
	}

	// Original method from ReedSolomonGenerator
	private static int multiply(int x, int y) {
		assert x >> 8 == 0 && y >> 8 == 0;
		// Russian peasant multiplication
		int z = 0;
		for (int i = 7; i >= 0; i--) {
			z = (z << 1) ^ ((z >>> 7) * 0x11D);
			z ^= ((y >>> i) & 1) * x;
		}
		assert z >>> 8 == 0;
		return z;
	}

	/**
	 * Straightforward implementation of the divisor polynomial, without the precomputed multiplication table that
	 * {@link ReedSolomonGenerator} builds in its constructor.
	 *
	 * @param degree degree
	 * @return coefficients, from highest to lowest power
	 */
	private static byte[] referenceCoefficients(int degree) {
		byte[] coefficients = new byte[degree];
		coefficients[degree - 1] = 1;
		int root = 1;
		for (int i = 0; i < degree; i++) {
			for (int j = 0; j < coefficients.length; j++) {
				coefficients[j] = (byte) multiply(coefficients[j] & 0xFF, root);
				if (j + 1 < coefficients.length)
					coefficients[j] ^= coefficients[j + 1];
			}
			root = multiply(root, 0x02);
		}
		return coefficients;
	}

	/**
	 * Straightforward polynomial division, against which the table-driven implementation is checked.
	 *
	 * @param data    data codewords
	 * @param dataOff offset into {@code data}
	 * @param dataLen number of codewords to consume
	 * @param degree  degree of the divisor polynomial
	 * @return error correction codewords
	 */
	private static byte[] referenceRemainder(byte[] data, int dataOff, int dataLen, int degree) {
		byte[] coefficients = referenceCoefficients(degree);
		byte[] result = new byte[degree];
		for (int i = dataOff; i < dataOff + dataLen; i++) {
			int factor = (data[i] ^ result[0]) & 0xFF;
			System.arraycopy(result, 1, result, 0, result.length - 1);
			result[result.length - 1] = 0;
			for (int j = 0; j < result.length; j++)
				result[j] ^= (byte) multiply(coefficients[j] & 0xFF, factor);
		}
		return result;
	}

	/**
	 * The worked example in ISO/IEC 18004 Annex I: the data codewords for {@code "01234567"} at version 1,
	 * error correction level M, and the ten error correction codewords the standard says they produce.
	 */
	@Test
	public void calculateRemainderMatchesStandardWorkedExample() {
		byte[] data = { (byte) 0x10, (byte) 0x20, (byte) 0x0C, (byte) 0x56, (byte) 0x61, (byte) 0x80, (byte) 0xEC,
				(byte) 0x11, (byte) 0xEC, (byte) 0x11, (byte) 0xEC, (byte) 0x11, (byte) 0xEC, (byte) 0x11, (byte) 0xEC,
				(byte) 0x11 };
		byte[] expected = { (byte) 0xA5, (byte) 0x24, (byte) 0xD4, (byte) 0xC1, (byte) 0xED, (byte) 0x36, (byte) 0xC7,
				(byte) 0x87, (byte) 0x2C, (byte) 0x55 };
		byte[] result = new byte[10];
		ReedSolomonGenerator.MEMOIZER.get(10).calculateRemainder(data, 0, data.length, result);
		assertArrayEquals(expected, result);
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 7, 10, 13, 15, 16, 17, 18, 20, 22, 24, 26, 28, 30, 255 })
	public void calculateRemainderMatchesReferenceImplementation(int degree) {
		Random random = new Random(degree);
		ReedSolomonGenerator generator = ReedSolomonGenerator.MEMOIZER.get(degree);
		for (int trial = 0; trial < 20; trial++) {
			byte[] data = new byte[1 + random.nextInt(64)];
			random.nextBytes(data);
			byte[] result = new byte[degree];
			generator.calculateRemainder(data, 0, data.length, result);
			assertArrayEquals(referenceRemainder(data, 0, data.length, degree), result,
					"degree " + degree + ", trial " + trial);
		}
		return;
	}

	/**
	 * {@code addEccAndInterleave()} calls this method with a window into a larger array, so the offset and length
	 * arguments need to be honoured rather than the whole array being consumed.
	 */
	@Test
	public void calculateRemainderHonoursOffsetAndLength() {
		Random random = new Random(1);
		byte[] data = new byte[64];
		random.nextBytes(data);
		for (int degree : DEGREES) {
			ReedSolomonGenerator generator = ReedSolomonGenerator.MEMOIZER.get(degree);
			byte[] result = new byte[degree];
			generator.calculateRemainder(data, 16, 32, result);
			assertArrayEquals(referenceRemainder(data, 16, 32, degree), result, "degree " + degree);

			// The same window copied to its own array must give the same answer.
			byte[] window = new byte[32];
			System.arraycopy(data, 16, window, 0, 32);
			byte[] windowResult = new byte[degree];
			generator.calculateRemainder(window, 0, window.length, windowResult);
			assertArrayEquals(result, windowResult, "degree " + degree);
		}
		return;
	}

	@Test
	public void calculateRemainderOfNoDataLeavesResultZeroed() {
		byte[] result = new byte[10];
		java.util.Arrays.fill(result, (byte) 0x7F);
		ReedSolomonGenerator.MEMOIZER.get(10).calculateRemainder(new byte[0], 0, 0, result);
		assertArrayEquals(new byte[10], result);
		return;
	}

	@Test
	public void calculateRemainderThrowsOnNulls() {
		ReedSolomonGenerator generator = ReedSolomonGenerator.MEMOIZER.get(10);
		assertThrows(NullPointerException.class, () -> generator.calculateRemainder(null, 0, 0, new byte[10]));
		assertThrows(NullPointerException.class, () -> generator.calculateRemainder(new byte[10], 0, 10, null));
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 256, 1000, Integer.MAX_VALUE })
	public void constructorThrowsWhenDegreeOutOfRange(int degree) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> ReedSolomonGenerator.MEMOIZER.get(degree));
		assertEquals("Degree out of range", e.getMessage());
		return;
	}

	@Test
	public void generatorsAreMemoised() {
		assertSame(ReedSolomonGenerator.MEMOIZER.get(10), ReedSolomonGenerator.MEMOIZER.get(10));
		return;
	}
}
