package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Unit tests on {@link DataTooLongException}.
 *
 * @author paulh
 * @since 1.1
 */
public class DataTooLongExceptionTest {
	@Test
	public void noArgumentConstructorLeavesMessageNull() {
		assertNull(new DataTooLongException().getMessage());
		return;
	}

	@Test
	public void constructorRetainsMessage() {
		assertEquals("too long", new DataTooLongException("too long").getMessage());
		return;
	}

	/**
	 * Callers catching {@link IllegalArgumentException} must also catch this, so the hierarchy is part of the
	 * public contract rather than an implementation detail.
	 */
	@Test
	public void isAnIllegalArgumentException() {
		assertTrue(IllegalArgumentException.class.isAssignableFrom(DataTooLongException.class));
		assertThrows(IllegalArgumentException.class, () -> {
			throw new DataTooLongException("too long");
		});
		return;
	}

	@Test
	public void isSerialisable() throws Exception {
		DataTooLongException original = new DataTooLongException("too long");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(original);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			DataTooLongException restored = (DataTooLongException) in.readObject();
			assertEquals("too long", restored.getMessage());
		}
		return;
	}
}
