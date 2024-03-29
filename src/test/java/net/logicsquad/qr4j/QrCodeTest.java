package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.logicsquad.qr4j.QrCode.Ecc;
import net.logicsquad.qr4j.QrCode.Template;

/**
 * Fast QR Code generator demo
 * 
 * Run this command-line program with no arguments. The program creates/overwrites a bunch of PNG and SVG
 * files in the current working directory to demonstrate the creation of QR Codes.
 * 
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
public class QrCodeTest {
	// Creates a single QR Code, then writes it to a PNG file and an SVG file.
	@Test
	public void doBasicDemo() throws IOException {
		String text = "Hello, world!";          // User-supplied Unicode text
		QrCode.Ecc errCorLvl = QrCode.Ecc.LOW;  // Error correction level
		
		QrCode qr = QrCode.encodeText(text, errCorLvl);  // Make the QR Code symbol
		
		BufferedImage img = qr.toImage(10, 4);          // Convert to bitmap image
		File imgFile = new File("hello-world-QR.png");   // File path for output
		ImageIO.write(img, "png", imgFile);              // Write image to file
		
		String svg = qr.toSvg(4, "#FFFFFF", "#000000");  // Convert to SVG XML code
		File svgFile = new File("hello-world-QR.svg");          // File path for output
		Files.write(svgFile.toPath(),                           // Write image to file
			svg.getBytes(StandardCharsets.UTF_8));
	}
	
	// Creates a variety of QR Codes that exercise different features of the library, and writes each one to file.
	@Test
	public void doVarietyDemo() throws IOException {
		QrCode qr;
		
		// Numeric mode encoding (3.33 bits per digit)
		qr = QrCode.encodeText("314159265358979323846264338327950288419716939937510", QrCode.Ecc.MEDIUM);
		writePng(qr.toImage(13, 1), "pi-digits-QR.png");
		
		// Alphanumeric mode encoding (5.5 bits per character)
		qr = QrCode.encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", QrCode.Ecc.HIGH);
		writePng(qr.toImage(10, 2), "alphanumeric-QR.png");
		
		// Unicode text as UTF-8
		qr = QrCode.encodeText("こんにちwa、世界！ αβγδ", QrCode.Ecc.QUARTILE);
		writePng(qr.toImage(10, 3), "unicode-QR.png");
		
		// Moderately large QR Code using longer text (from Lewis Carroll's Alice in Wonderland)
		qr = QrCode.encodeText(
			"Alice was beginning to get very tired of sitting by her sister on the bank, "
			+ "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			+ "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			+ "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			+ "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			+ "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			+ "a White Rabbit with pink eyes ran close by her.", QrCode.Ecc.HIGH);
		writePng(qr.toImage(6, 10), "alice-wonderland-QR.png");
	}

	// Creates QR Codes with manually specified segments for better compactness.
	@Test
	public void doSegmentDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Illustration "silver"
		String silver0 = "THE SQUARE ROOT OF 2 IS 1.";
		String silver1 = "41421356237309504880168872420969807856967187537694807317667973799";
		qr = QrCode.encodeText(silver0 + silver1, QrCode.Ecc.LOW);
		writePng(qr.toImage(10, 3), "sqrt2-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeAlphanumeric(silver0),
			QrSegment.makeNumeric(silver1));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(10, 3), "sqrt2-segmented-QR.png");
		
		// Illustration "golden"
		String golden0 = "Golden ratio φ = 1.";
		String golden1 = "6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374";
		String golden2 = "......";
		qr = QrCode.encodeText(golden0 + golden1 + golden2, QrCode.Ecc.LOW);
		writePng(qr.toImage(8, 5), "phi-monolithic-QR.png");
		
		segs = Arrays.asList(
			QrSegment.makeBytes(golden0.getBytes(StandardCharsets.UTF_8)),
			QrSegment.makeNumeric(golden1),
			QrSegment.makeAlphanumeric(golden2));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(8, 5), "phi-segmented-QR.png");
		
		// Illustration "Madoka": kanji, kana, Cyrillic, full-width Latin, Greek characters
		String madoka = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";
		qr = QrCode.encodeText(madoka, QrCode.Ecc.LOW);
		writePng(qr.toImage(9, 4, 0xFFFFE0, 0x303080), "madoka-utf8-QR.png");
		
		segs = Arrays.asList(QrSegment.Utility.makeKanji(madoka));
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.LOW);
		writePng(qr.toImage(9, 4, 0xE0F0FF, 0x404040), "madoka-kanji-QR.png");
	}

	// Creates QR Codes with the same size and contents but different mask patterns.
	@Test
	public void doMaskDemo() throws IOException {
		QrCode qr;
		List<QrSegment> segs;
		
		// Project Nayuki URL
		segs = QrSegment.makeSegments("https://www.nayuki.io/");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true);  // Automatic mask
		writePng(qr.toImage(8, 6, 0xE0FFE0, 0x206020), "project-nayuki-automask-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 3, true);  // Force mask 3
		writePng(qr.toImage(8, 6, 0xFFE0E0, 0x602020), "project-nayuki-mask3-QR.png");
		
		// Chinese text as UTF-8
		segs = QrSegment.makeSegments("維基百科（Wikipedia，聆聽i/ˌwɪkᵻˈpiːdi.ə/）是一個自由內容、公開編輯且多語言的網路百科全書協作計畫");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 0, true);  // Force mask 0
		writePng(qr.toImage(10, 3), "unicode-mask0-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 1, true);  // Force mask 1
		writePng(qr.toImage(10, 3), "unicode-mask1-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 5, true);  // Force mask 5
		writePng(qr.toImage(10, 3), "unicode-mask5-QR.png");
		qr = QrCode.encodeSegments(segs, QrCode.Ecc.MEDIUM, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 7, true);  // Force mask 7
		writePng(qr.toImage(10, 3), "unicode-mask7-QR.png");
	}

	// Helper function to reduce code duplication.
	private static void writePng(BufferedImage img, String filepath) throws IOException {
		ImageIO.write(img, "png", new File(filepath));
	}

	// Tests the lookup table against the original method
	@Test
	public void getNumDataCodewordsTest() {
		for (Ecc errorCorrectionLevel : Ecc.values()) {
			for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
				assertEquals(getNumDataCodewords(version, errorCorrectionLevel), QrCode.getNumDataCodewords(version, errorCorrectionLevel));
			}
		}
	}

	// Original method from QrCode
	private static int getNumDataCodewords(int version, Ecc errorCorrectionLevel) {
		return Template.getNumRawDataModules(version) / 8 - QrCode.ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version]
				* QrCode.NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
	}

	// Tests the lookup table against the original method
	@Test
	public void getAlignmentPatternPositionsTest() {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			assertArrayEquals(getAlignmentPatternPositions(version), QrCode.Template.getAlignmentPatternPositions(version));
		}
		return;
	}

	// Original method from QrCode
	private static int[] getAlignmentPatternPositions(int version) {
		if (version == 1)
			return new int[]{};
		else {
			int size = version * 4 + 17;
			int numAlign = version / 7 + 2;
			int step = (version == 32) ? 26 :
				(version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
			int[] result = new int[numAlign];
			result[0] = 6;
			for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
				result[i] = pos;
			return result;
		}
	}

	// Tests the lookup table against the original method
	@Test
	public void getNumRawDataModulesTest() {
		for (int version = QrCode.MIN_VERSION; version <= QrCode.MAX_VERSION; version++) {
			assertEquals(getNumRawDataModules(version), QrCode.Template.getNumRawDataModules(version));
		}
		return;
	}

	// Original method from QrCode
	private static int getNumRawDataModules(int version) {
		if (version < QrCode.MIN_VERSION || version > QrCode.MAX_VERSION)
			throw new IllegalArgumentException("Version number out of range");
		int result = (16 * version + 128) * version + 64;
		if (version >= 2) {
			int numAlign = version / 7 + 2;
			result -= (25 * numAlign - 10) * numAlign - 55;
			if (version >= 7)
				result -= 36;
		}
		return result;
	}
}
