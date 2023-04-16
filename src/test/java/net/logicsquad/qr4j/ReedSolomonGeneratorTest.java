package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests on {@link ReedSolomonGenerator}.
 * 
 * @author paulh
 * @since 1.0
 */
public class ReedSolomonGeneratorTest {
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
}
