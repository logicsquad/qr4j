package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.logicsquad.qr4j.QrSegment.Mode;

/**
 * Unit tests on {@link QrSegment}.
 * 
 * @author paulh
 * @since 1.0
 */
public class QrSegmentTest {
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
		return;
	}

	@Test
	public void makeAlphanumericReturnsQrSegmentForLegalArgument() {
		assertNotNull(QrSegment.makeAlphanumeric("FOO"));
		assertNotNull(QrSegment.makeAlphanumeric("F12 LK87"));
		return;
	}

	@Test
	public void makeNumericThrowsOnIllegalArgument() {
		assertThrows(NullPointerException.class, () -> QrSegment.makeNumeric(null));
		assertThrows(IllegalArgumentException.class, () -> QrSegment.makeNumeric("foo"));
		return;
	}

	@Test
	public void makeNumericReturnsQrSegmentForLegalArgument() {
		assertNotNull(QrSegment.makeAlphanumeric("1234567890"));
		return;
	}
}
