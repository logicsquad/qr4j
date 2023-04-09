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
		return;
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
	 * Appends {@code length} low-order bits of {@code value} to this buffer. Requires {@code 0 <= length <= 31}
	 * and {@code 0 <= value < 2^length}.
	 * 
	 * @param value  source of bits
	 * @param length number of bits to append
	 */
	public void appendBits(int value, int length) {
		if (length < 0 || length > 31 || value >>> length != 0)
			throw new IllegalArgumentException("Value out of range");
		if (length > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		if (bitLength + length + 1 > data.length << 5)
			data = Arrays.copyOf(data, data.length * 2);
		assert bitLength + length <= data.length << 5;
		
		int remain = 32 - (bitLength & 0x1F);
		assert 1 <= remain && remain <= 32;
		if (remain < length) {
			data[bitLength >>> 5] |= value >>> (length - remain);
			bitLength += remain;
			assert (bitLength & 0x1F) == 0;
			length -= remain;
			value &= (1 << length) - 1;
			remain = 32;
		}
		data[bitLength >>> 5] |= value << (remain - length);
		bitLength += length;
		return;
	}

	/**
	 * Appends to this buffer {@code length} bits from the sequence of bits represented by {@code values}.
	 * Requires {@code 0 <= length <= 32 * values.length}.
	 * 
	 * @param values word array
	 * @param length number of bits to append
	 */
	public void appendBits(int[] values, int length) {
		Objects.requireNonNull(values);
		if (length == 0)
			return;
		if (length < 0 || length > values.length * 32L)
			throw new IllegalArgumentException("Value out of range");
		int wholeWords = length / 32;
		int tailBits = length % 32;
		if (tailBits > 0 && values[wholeWords] << tailBits != 0)
			throw new IllegalArgumentException("Last word must have low bits clear");
		if (length > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		while (bitLength + length > data.length * 32)
			data = Arrays.copyOf(data, data.length * 2);
		
		int shift = bitLength % 32;
		if (shift == 0) {
			System.arraycopy(values, 0, data, bitLength / 32, (length + 31) / 32);
			bitLength += length;
		} else {
			for (int i = 0; i < wholeWords; i++) {
				int word = values[i];
				data[bitLength >>> 5] |= word >>> shift;
				bitLength += 32;
				data[bitLength >>> 5] = word << (32 - shift);
			}
			if (tailBits > 0)
				appendBits(values[wholeWords] >>> (32 - tailBits), tailBits);
		}
		return;
	}
}
