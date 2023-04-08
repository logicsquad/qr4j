package net.logicsquad.qr4j;

import java.util.Arrays;
import java.util.Objects;

/**
 * An appendable sequence of bits.
 * 
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
final class BitBuffer {
	// In each 32-bit word, bits are filled from top down.
	/**
	 * Container for bits
	 */
	int[] data;  

	// Always non-negative.
	/**
	 * Count of bits represented
	 */
	int bitLength;

	/**
	 * Constructor creating an empty {@code BitBuffer}
	 */
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}

	/**
	 * Returns bit at {@code index}, yielding 0 or 1.
	 * 
	 * @param index index into buffer
	 * @return bit value
	 * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, bitLength)}
	 */
	public int getBit(int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}

	/**
	 * Returns a new array representing this {@code BitBuffer}'s bits packed into {@code byte}s (in big-endian
	 * ordering). The current {@code bitLength} must be a multiple of 8.
	 * 
	 * @return bits packed into array of {@code byte}s
	 * @throws IllegalStateException if this {@code BitBuffer} doesn't contain a whole number of bytes
	 */
	public byte[] getBytes() {
		if (bitLength % 8 != 0)
			throw new IllegalStateException("Data is not a whole number of bytes");
		byte[] result = new byte[bitLength / 8];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)(data[i >>> 2] >>> (~i << 3));
		return result;
	}

	/**
	 * Appends {@code len} low-order bits of {@code val} to this buffer. Requires {@code 0 <= len <= 31} and
	 * {@code 0 <= val < 2^len}.
	 * 
	 * @param val source of bits
	 * @param len number of bits to append
	 */
	public void appendBits(int val, int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		if (bitLength + len + 1 > data.length << 5)
			data = Arrays.copyOf(data, data.length * 2);
		assert bitLength + len <= data.length << 5;
		
		int remain = 32 - (bitLength & 0x1F);
		assert 1 <= remain && remain <= 32;
		if (remain < len) {
			data[bitLength >>> 5] |= val >>> (len - remain);
			bitLength += remain;
			assert (bitLength & 0x1F) == 0;
			len -= remain;
			val &= (1 << len) - 1;
			remain = 32;
		}
		data[bitLength >>> 5] |= val << (remain - len);
		bitLength += len;
	}

	/**
	 * Appends to this buffer {@code len} bits from the sequence of bits represented by {@code val}. Requires
	 * {@code 0 <= len <= 32 * vals.length}.
	 * 
	 * @param vals word array
	 * @param len number of bits to append
	 */
	public void appendBits(int[] vals, int len) {
		Objects.requireNonNull(vals);
		if (len == 0)
			return;
		if (len < 0 || len > vals.length * 32L)
			throw new IllegalArgumentException("Value out of range");
		int wholeWords = len / 32;
		int tailBits = len % 32;
		if (tailBits > 0 && vals[wholeWords] << tailBits != 0)
			throw new IllegalArgumentException("Last word must have low bits clear");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		while (bitLength + len > data.length * 32)
			data = Arrays.copyOf(data, data.length * 2);
		
		int shift = bitLength % 32;
		if (shift == 0) {
			System.arraycopy(vals, 0, data, bitLength / 32, (len + 31) / 32);
			bitLength += len;
		} else {
			for (int i = 0; i < wholeWords; i++) {
				int word = vals[i];
				data[bitLength >>> 5] |= word >>> shift;
				bitLength += 32;
				data[bitLength >>> 5] = word << (32 - shift);
			}
			if (tailBits > 0)
				appendBits(vals[wholeWords] >>> (32 - tailBits), tailBits);
		}
	}
}
