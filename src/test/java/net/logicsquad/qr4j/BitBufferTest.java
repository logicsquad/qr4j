package net.logicsquad.qr4j;

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

	@Test
	public void canCreateNewObject() {
		assertNotNull(buffer);
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
	public void appendBitsArrayThrowsIfLenOutOfRange() {
		int[] vals = { 123, 2209, 877, 87766 };
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, -1));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, -10));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, 129));
		assertThrows(IllegalArgumentException.class, () -> buffer.appendBits(vals, 300));
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
}
