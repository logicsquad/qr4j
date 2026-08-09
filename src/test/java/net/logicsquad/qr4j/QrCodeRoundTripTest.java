package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.Decoder;

import net.logicsquad.qr4j.QrCode.Ecc;

/**
 * Round-trip tests: encode with QR4J, decode with ZXing, and check we get back what we put in.
 *
 * <p>
 * These are the only tests in the suite that check QR4J's output against an independent implementation of the
 * specification, rather than against QR4J itself. Between them they exercise the Reed-Solomon codewords, block
 * interleaving, the zigzag data placement, format and version bits, and mask application.
 * </p>
 *
 * @author paulh
 * @since 1.1
 */
public class QrCodeRoundTripTest {
	/**
	 * Payloads chosen to exercise each segment mode and the mode-switching logic.
	 */
	private static final String[] TEXTS = {
		"314159265358979323846264338327950288419716939937510",
		"DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/",
		"Hello, world!",
		"https://logicsquad.net/",
		"THE SQUARE ROOT OF 2 IS 1.41421356237309504880168872420969807856967187537694807317667973799",
		"こんにちwa、世界！ αβγδ",
		"Golden ratio φ = 1.6180339887",
		"維基百科",
	};

	/**
	 * Text that can be encoded in kanji mode.
	 */
	private static final String MADOKA = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";

	/**
	 * Decodes {@code qr} directly from its module grid. This bypasses rendering and image binarisation entirely,
	 * so a failure here is a fault in the encoder rather than in {@link QrCode#toImage(int, int)}.
	 *
	 * @param qr a {@link QrCode}
	 * @return decoded result
	 * @throws Exception if {@code qr} cannot be decoded
	 */
	private static DecoderResult decode(QrCode qr) throws Exception {
		BitMatrix matrix = new BitMatrix(qr.size);
		for (int y = 0; y < qr.size; y++) {
			for (int x = 0; x < qr.size; x++) {
				if (qr.getModule(x, y))
					matrix.set(x, y);
			}
		}
		return new Decoder().decode(matrix);
	}

	/**
	 * Decodes the text content of {@code qr}.
	 *
	 * @param qr a {@link QrCode}
	 * @return decoded text
	 * @throws Exception if {@code qr} cannot be decoded
	 */
	private static String decodeText(QrCode qr) throws Exception {
		return decode(qr).getText();
	}

	private static Stream<Arguments> textAndEcc() {
		return Stream.of(TEXTS).flatMap(text -> Stream.of(Ecc.values()).map(ecl -> Arguments.of(text, ecl)));
	}

	@ParameterizedTest
	@MethodSource("textAndEcc")
	public void encodeTextRoundTrips(String text, Ecc ecl) throws Exception {
		assertEquals(text, decodeText(QrCode.encodeText(text, ecl)));
		return;
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7 })
	public void everyForcedMaskRoundTrips(int mask) throws Exception {
		List<QrSegment> segments = QrSegment.makeSegments("https://logicsquad.net/");
		QrCode qr = QrCode.encodeSegments(segments, Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, mask, true);
		assertEquals(mask, qr.mask);
		assertEquals("https://logicsquad.net/", decodeText(qr));
		return;
	}

	/**
	 * Every version from 1 to 40 should produce a decodable symbol. Payload length is chosen to force each
	 * version in turn, which exercises the alignment pattern positions and (from version 7) the version bits.
	 */
	@Test
	public void everyVersionRoundTrips() throws Exception {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < QrCode.getNumDataCodewords(version, Ecc.LOW) - 2; i++)
				sb.append((char) ('0' + i % 10));
			String text = sb.toString();
			QrCode qr = QrCode.encodeSegments(QrSegment.makeSegments(text), Ecc.LOW, version, version, -1, false);
			assertEquals(version, qr.version);
			assertEquals(text, decodeText(qr), "failed at version " + version);
		}
		return;
	}

	/**
	 * A hand-built kanji mode segment should decode back to the original text. Nothing else in the suite checks
	 * that {@link QrSegment.Utility#makeKanji(String)} produces correct Shift JIS output.
	 */
	@Test
	public void kanjiSegmentRoundTrips() throws Exception {
		QrCode qr = QrCode.encodeSegments(Arrays.asList(QrSegment.Utility.makeKanji(MADOKA)), Ecc.LOW);
		assertEquals(MADOKA, decodeText(qr));
		return;
	}

	/**
	 * Kanji mode should encode this text more compactly than the UTF-8 byte mode chosen automatically by
	 * {@link QrSegment#makeSegments(String)}.
	 */
	@Test
	public void kanjiSegmentIsMoreCompactThanUtf8() throws Exception {
		QrCode kanji = QrCode.encodeSegments(Arrays.asList(QrSegment.Utility.makeKanji(MADOKA)), Ecc.LOW);
		QrCode utf8 = QrCode.encodeText(MADOKA, Ecc.LOW);
		assertTrue(kanji.version < utf8.version,
				"kanji version " + kanji.version + " should be smaller than UTF-8 version " + utf8.version);
		assertEquals(MADOKA, decodeText(kanji));
		assertEquals(MADOKA, decodeText(utf8));
		return;
	}

	/**
	 * An explicit ECI segment declaring UTF-8 (assignment value 26) should be understood by a decoder.
	 */
	@Test
	public void eciSegmentRoundTrips() throws Exception {
		String text = "こんにちwa、世界！ αβγδ";
		List<QrSegment> segments = new ArrayList<>();
		segments.add(QrSegment.makeEci(26));
		segments.addAll(QrSegment.makeSegments(text));
		assertEquals(text, decodeText(QrCode.encodeSegments(segments, Ecc.LOW)));
		return;
	}

	/**
	 * Segments produced by {@link QrSegment.Utility#makeSegmentsOptimally(String, QrCode.Ecc, int, int)} must
	 * still decode correctly, including for the mixed-mode text that previously threw.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "Hello, world!", "abc123XYZ", "Golden ratio φ = 1.6180339887",
			"THE SQUARE ROOT OF 2 IS 1.41421356237309504880168872420969807856967187537694807317667973799" })
	public void optimallySegmentedTextRoundTrips(String text) throws Exception {
		List<QrSegment> segments = QrSegment.Utility.makeSegmentsOptimally(text, Ecc.LOW, QrCode.MIN_VERSION, QrCode.MAX_VERSION);
		assertEquals(text, decodeText(QrCode.encodeSegments(segments, Ecc.LOW)));
		return;
	}

	@Test
	public void encodeBinaryRoundTrips() throws Exception {
		byte[] data = new byte[256];
		for (int i = 0; i < data.length; i++)
			data[i] = (byte) i;
		QrCode qr = QrCode.encodeBinary(data, Ecc.LOW);
		List<byte[]> segments = decode(qr).getByteSegments();
		assertEquals(1, segments.size());
		assertArrayEquals(data, segments.get(0));
		return;
	}

	/**
	 * Decoding a code rendered as an image additionally exercises {@link QrCode#toImage(int, int)} and the finder
	 * patterns, since ZXing has to locate the symbol in the image before it can decode it.
	 */
	@Test
	public void renderedImageRoundTrips() throws Exception {
		String text = "Hello, world!";
		QrCode qr = QrCode.encodeText(text, Ecc.MEDIUM);
		BufferedImage image = qr.toImage(8, 4);
		LuminanceSource source = new BufferedImageLuminanceSource(image);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		assertEquals(text, new QRCodeReader().decode(bitmap).getText());
		return;
	}

	/**
	 * Minimal {@link LuminanceSource} over a {@link BufferedImage}, so that we can decode rendered output without
	 * pulling in ZXing's {@code javase} module.
	 */
	private static final class BufferedImageLuminanceSource extends LuminanceSource {
		private final BufferedImage image;

		BufferedImageLuminanceSource(BufferedImage image) {
			super(image.getWidth(), image.getHeight());
			this.image = image;
			return;
		}

		@Override
		public byte[] getRow(int y, byte[] row) {
			if (row == null || row.length < getWidth())
				row = new byte[getWidth()];
			for (int x = 0; x < getWidth(); x++)
				row[x] = luminance(image.getRGB(x, y));
			return row;
		}

		@Override
		public byte[] getMatrix() {
			byte[] matrix = new byte[getWidth() * getHeight()];
			for (int y = 0; y < getHeight(); y++) {
				for (int x = 0; x < getWidth(); x++)
					matrix[y * getWidth() + x] = luminance(image.getRGB(x, y));
			}
			return matrix;
		}

		private static byte luminance(int rgb) {
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			return (byte) ((r + 2 * g + b) / 4);
		}
	}
}
