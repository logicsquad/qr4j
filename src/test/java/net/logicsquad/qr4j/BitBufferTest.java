package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests on {@link BitBuffer}.
 *
 * @author paulh
 * @since 1.0
 */
public class BitBufferTest {
	private BitBuffer buffer;

	@BeforeEach
	public void setup() {
		buffer = new BitBuffer();
		return;
	}

	/**
	 * Returns the content of {@code buffer} as a string of {@code '0'} and {@code '1'} characters.
	 *
	 * @param buffer a {@link BitBuffer}
	 * @return buffer content, most significant bit first
	 */
	private static String bits(BitBuffer buffer) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < buffer.bitLength; i++)
			sb.append(buffer.getBit(i));
		return sb.toString();
	}

	@Test
	public void canCreateNewObject() {
		assertNotNull(buffer);
		return;
	}

	@Test
	public void newBufferIsEmpty() {
		assertEquals(0, buffer.bitLength);
		assertEquals("", bits(buffer));
		assertEquals(0, buffer.getBytes().length);
		return;
	}

	@Test
	public void appendBitsThrowsIfLenOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(127, -1));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(127, -10));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(127, 32));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(127, 100));
		return;
	}

	@Test
	public void appendBitsThrowsIfValOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(-127, 3));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(127, 2));
		return;
	}

	@Test
	public void appendBitsAppendsExpectedBits() {
		buffer.appendBits(0b101, 3);
		assertEquals("101", bits(buffer));
		buffer.appendBits(0b1100, 4);
		assertEquals("1011100", bits(buffer));
		assertEquals(7, buffer.bitLength);
		return;
	}

	@Test
	public void appendBitsOfZeroLengthIsNoOp() {
		buffer.appendBits(0, 0);
		assertEquals(0, buffer.bitLength);
		buffer.appendBits(0b11, 2);
		buffer.appendBits(0, 0);
		assertEquals("11", bits(buffer));
		return;
	}

	/**
	 * The buffer starts at 64 words and must grow to accommodate more than 2048 bits.
	 */
	@Test
	public void appendBitsGrowsBufferBeyondInitialCapacity() {
		for (int i = 0; i < 100; i++)
			buffer.appendBits(0x7FFFFFFF, 31);
		assertEquals(3100, buffer.bitLength);
		for (int i = 0; i < buffer.bitLength; i++)
			assertEquals(1, buffer.getBit(i), "bit " + i);
		return;
	}

	@Test
	public void appendBitsArrayThrowsIfLenOutOfRange() {
		int[] vals = { 123, 2209, 877, 87766 };
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, -1));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, -10));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, 129));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, 300));
		return;
	}

	@Test
	public void appendBitsArrayThrowsOnNull() {
		assertThrows(NullPointerException.class, () -> buffer.appendBits((int[]) null, 8));
		return;
	}

	/**
	 * A partial final word must have its unused low bits cleared, since they would otherwise be appended.
	 */
	@Test
	public void appendBitsArrayThrowsIfLastWordHasLowBitsSet() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> buffer.appendBits(new int[] { 0x0F000000 }, 4));
		assertEquals("Last word must have low bits clear", e.getMessage());
		return;
	}

	@Test
	public void appendBitsArrayOfZeroLengthIsNoOp() {
		buffer.appendBits(new int[] { 0x12345678 }, 0);
		assertEquals(0, buffer.bitLength);
		return;
	}

	/**
	 * When the buffer is word-aligned the words are copied directly.
	 */
	@Test
	public void appendBitsArrayAppendsWhenWordAligned() {
		buffer.appendBits(new int[] { 0x80000000 }, 1);
		assertEquals("1", bits(buffer));

		buffer = new BitBuffer();
		buffer.appendBits(new int[] { 0xF0000000 }, 4);
		assertEquals("1111", bits(buffer));

		buffer = new BitBuffer();
		buffer.appendBits(new int[] { 0xFFFFFFFF }, 32);
		assertEquals(32, buffer.bitLength);

		buffer = new BitBuffer();
		buffer.appendBits(new int[] { 0xAAAAAAAA, 0xFF000000 }, 40);
		assertEquals("1010101010101010101010101010101011111111", bits(buffer));
		return;
	}

	/**
	 * When the buffer is not word-aligned each word has to be split across two buffer words, which is a separate
	 * code path from the aligned case above.
	 */
	@Test
	public void appendBitsArrayAppendsWhenNotWordAligned() {
		buffer.appendBits(0b101, 3);
		buffer.appendBits(new int[] { 0xAAAAAAAA }, 32);
		assertEquals("10110101010101010101010101010101010", bits(buffer));
		assertEquals(35, buffer.bitLength);

		buffer = new BitBuffer();
		buffer.appendBits(0b101, 3);
		buffer.appendBits(new int[] { 0xAAAAAAAA, 0xC0000000 }, 34);
		assertEquals("1011010101010101010101010101010101011", bits(buffer));
		assertEquals(37, buffer.bitLength);
		return;
	}

	@Test
	public void appendBitsArrayGrowsBufferBeyondInitialCapacity() {
		for (int i = 0; i < 100; i++)
			buffer.appendBits(new int[] { 0xFFFFFFFF }, 32);
		assertEquals(3200, buffer.bitLength);
		for (int i = 0; i < buffer.bitLength; i++)
			assertEquals(1, buffer.getBit(i), "bit " + i);
		return;
	}

	@Test
	public void getBitThrowsIfIndexOutOfRange() {
		buffer.appendBits(56556, 16);
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBit(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBit(16));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBit(17));
		return;
	}

	@Test
	public void getBitReturnsExpectedBit() {
		buffer.appendBits(56556, 16);
		assertEquals(1, buffer.getBit(0));
		assertEquals(1, buffer.getBit(1));
		assertEquals(0, buffer.getBit(2));
		assertEquals(1, buffer.getBit(3));
		assertEquals(1, buffer.getBit(4));
		assertEquals(1, buffer.getBit(5));
		assertEquals(0, buffer.getBit(6));
		assertEquals(0, buffer.getBit(7));
		assertEquals(1, buffer.getBit(8));
		assertEquals(1, buffer.getBit(9));
		assertEquals(1, buffer.getBit(10));
		assertEquals(0, buffer.getBit(11));
		assertEquals(1, buffer.getBit(12));
		assertEquals(1, buffer.getBit(13));
		assertEquals(0, buffer.getBit(14));
		assertEquals(0, buffer.getBit(15));
		return;
	}

	@Test
	public void getBytesThrowsIfDataNotWholeNumberOfBytes() {
		buffer.appendBits(7, 3);
		assertThrows(IllegalStateException.class, () -> buffer.getBytes());
		return;
	}

	@Test
	public void getBytesReturnsExpectedBytes() {
		buffer.appendBits(56556, 16);
		byte[] bytes = buffer.getBytes();
		assertEquals(2, bytes.length);
		assertEquals(-36, bytes[0]);
		assertEquals(-20, bytes[1]);
		return;
	}

	@Test
	public void getBytesReturnsExpectedBytesAcrossWordBoundary() {
		for (int i = 0; i < 5; i++)
			buffer.appendBits(0xAB, 8);
		assertArrayEquals(new byte[] { (byte) 0xAB, (byte) 0xAB, (byte) 0xAB, (byte) 0xAB, (byte) 0xAB },
				buffer.getBytes());
		return;
	}
}
