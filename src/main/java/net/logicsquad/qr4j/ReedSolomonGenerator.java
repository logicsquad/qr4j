package net.logicsquad.qr4j;

import java.util.Arrays;
import java.util.Objects;


// Computes Reed-Solomon error correction codewords for given data codewords.
final class ReedSolomonGenerator {
	
	// Use this memoizer to get instances of this class.
	public static final Memoizer<Integer,ReedSolomonGenerator> MEMOIZER
		= new Memoizer<>(ReedSolomonGenerator::new);
	
	
	// A table of size 256 * degree, where polynomialMultiply[i][j] = multiply(i, coefficients[j]).
	// 'coefficients' is the temporary array computed in the constructor.
	private byte[][] polynomialMultiply;
	
	
	// Creates a Reed-Solomon ECC generator polynomial for the given degree.
	private ReedSolomonGenerator(int degree) {
		if (degree < 1 || degree > 255)
			throw new IllegalArgumentException("Degree out of range");
		
		// The divisor polynomial, whose coefficients are stored from highest to lowest power.
		// For example, x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
		byte[] coefficients = new byte[degree];
		coefficients[degree - 1] = 1;  // Start off with the monomial x^0
		
		// Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
		// and drop the highest monomial term which is always 1x^degree.
		// Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
		int root = 1;
		for (int i = 0; i < degree; i++) {
			// Multiply the current product by (x - r^i)
			for (int j = 0; j < coefficients.length; j++) {
				coefficients[j] = (byte)multiply(coefficients[j] & 0xFF, root);
				if (j + 1 < coefficients.length)
					coefficients[j] ^= coefficients[j + 1];
			}
			root = multiply(root, 0x02);
		}
		
		polynomialMultiply = new byte[256][degree];
		for (int i = 0; i < polynomialMultiply.length; i++) {
			for (int j = 0; j < degree; j++)
				polynomialMultiply[i][j] = (byte)multiply(i, coefficients[j] & 0xFF);
		}
	}
	
	
	// Returns the error correction codeword for the given data polynomial and this divisor polynomial.
	public void getRemainder(byte[] data, int dataOff, int dataLen, byte[] result) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(result);
		int degree = polynomialMultiply[0].length;
		assert result.length == degree;
		
		Arrays.fill(result, (byte)0);
		for (int i = dataOff, dataEnd = dataOff + dataLen; i < dataEnd; i++) {  // Polynomial division
			byte[] table = polynomialMultiply[(data[i] ^ result[0]) & 0xFF];
			for (int j = 0; j < degree - 1; j++)
				result[j] = (byte)(result[j + 1] ^ table[j]);
			result[degree - 1] = table[degree - 1];
		}
	}
	
	
	// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
	// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
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
