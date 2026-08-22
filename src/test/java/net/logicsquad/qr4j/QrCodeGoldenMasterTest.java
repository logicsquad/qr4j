package net.logicsquad.qr4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.logicsquad.qr4j.QrCode.Ecc;

/**
 * <p>
 * Characterisation tests pinning the exact module grid produced for a fixed corpus of inputs. Each fixture is a
 * plain text rendering of the symbol held in {@code src/test/resources/golden}, so a behaviour change shows up as
 * a readable diff rather than an opaque hash mismatch.
 * </p>
 *
 * <p>
 * These fixtures record what QR4J currently produces. That is only worth pinning because
 * {@link QrCodeRoundTripTest} independently establishes that what QR4J produces is <em>correct</em>: the two test
 * classes are meant to be read together. A failure here is not necessarily a bug, but it is always a deliberate
 * decision to make.
 * </p>
 *
 * <p>
 * To regenerate the fixtures after an intended change, run:
 * </p>
 *
 * <pre>mvn test -Dtest=QrCodeGoldenMasterTest -Dqr4j.golden.regenerate=true</pre>
 *
 * <p>
 * ...then review the resulting diff carefully before committing it.
 * </p>
 *
 * @author paulh
 * @since 1.1
 */
public class QrCodeGoldenMasterTest {
	/**
	 * System property that causes fixtures to be rewritten rather than asserted against.
	 */
	private static final String REGENERATE_PROPERTY = "qr4j.golden.regenerate";

	/**
	 * Location of fixtures within the source tree, used only when regenerating.
	 */
	private static final Path FIXTURE_SOURCE_DIRECTORY = Paths.get("src", "test", "resources", "golden");

	/**
	 * Text from Lewis Carroll's <em>Alice's Adventures in Wonderland</em>, used as a large-version fixture.
	 */
	private static final String ALICE = "Alice was beginning to get very tired of sitting by her sister on the bank, "
			+ "and of having nothing to do: once or twice she had peeped into the book her sister was reading, "
			+ "but it had no pictures or conversations in it, 'and what is the use of a book,' thought Alice "
			+ "'without pictures or conversations?' So she was considering in her own mind (as well as she could, "
			+ "for the hot day made her feel very sleepy and stupid), whether the pleasure of making a "
			+ "daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly "
			+ "a White Rabbit with pink eyes ran close by her.";

	/**
	 * Text encodable in kanji mode.
	 */
	private static final String MADOKA = "「魔法少女まどか☆マギカ」って、　ИАИ　ｄｅｓｕ　κα？";

	/**
	 * The corpus. Names double as fixture file names; the inputs are inherited from the demo code this test class
	 * replaced, and between them cover every segment mode, multi-segment encoding, forced and automatic masking,
	 * and a range of versions.
	 *
	 * @return corpus as {@link Arguments} of name and {@link QrCode}
	 */
	private static Stream<Arguments> corpus() {
		return Stream.of(
			Arguments.of("hello-world-low", QrCode.encodeText("Hello, world!", Ecc.LOW)),
			Arguments.of("pi-digits-medium",
					QrCode.encodeText("314159265358979323846264338327950288419716939937510", Ecc.MEDIUM)),
			Arguments.of("alphanumeric-high",
					QrCode.encodeText("DOLLAR-AMOUNT:$39.87 PERCENTAGE:100.00% OPERATIONS:+-*/", Ecc.HIGH)),
			Arguments.of("unicode-quartile", QrCode.encodeText("こんにちwa、世界！ αβγδ", Ecc.QUARTILE)),
			Arguments.of("sqrt2-segmented-low", QrCode.encodeSegments(Arrays.asList(
					QrSegment.makeAlphanumeric("THE SQUARE ROOT OF 2 IS 1."),
					QrSegment.makeNumeric("41421356237309504880168872420969807856967187537694807317667973799")),
					Ecc.LOW)),
			Arguments.of("madoka-kanji-low",
					QrCode.encodeSegments(Arrays.asList(QrSegment.Utility.makeKanji(MADOKA)), Ecc.LOW)),
			Arguments.of("nayuki-mask3-high", QrCode.encodeSegments(QrSegment.makeSegments("https://www.nayuki.io/"),
					Ecc.HIGH, QrCode.MIN_VERSION, QrCode.MAX_VERSION, 3, true)),
			Arguments.of("alice-high", QrCode.encodeText(ALICE, Ecc.HIGH)));
	}

	/**
	 * Renders {@code qr} as one line per row, using {@code #} for dark modules and {@code .} for light.
	 *
	 * @param qr a {@link QrCode}
	 * @return text rendering of the module grid
	 */
	private static String render(QrCode qr) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < qr.size; y++) {
			for (int x = 0; x < qr.size; x++)
				sb.append(qr.getModule(x, y) ? '#' : '.');
			sb.append('\n');
		}
		return sb.toString();
	}

	/**
	 * <p>
	 * Reads the fixture with the given file name from the test classpath, normalising its line endings to
	 * {@code \n}.
	 * </p>
	 *
	 * <p>
	 * A Git client configured to translate line endings on checkout (the default on Windows) will present these
	 * fixtures with CRLF endings, which would otherwise fail every comparison for a reason that has nothing to do
	 * with QR4J. {@code .gitattributes} pins the fixtures to LF so this should not arise, but normalising here
	 * means the tests do not silently depend on the developer's Git configuration.
	 * </p>
	 *
	 * <p>
	 * Note carefully that only the <em>expected</em> side is normalised. Generated output is always compared
	 * verbatim, so a stray carriage return in what {@link QrCode#toSvg(int, String, String)} produces is still a
	 * failure, which is the whole point of keeping the {@code .svg} fixtures and the Windows CI job.
	 * </p>
	 *
	 * @param fileName fixture file name
	 * @return fixture content, with any CRLF line endings reduced to LF
	 * @throws IOException if the fixture cannot be read
	 */
	private static String readFixture(String fileName) throws IOException {
		String resource = "/golden/" + fileName;
		try (InputStream in = QrCodeGoldenMasterTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "missing fixture " + resource + " (regenerate with -D" + REGENERATE_PROPERTY + "=true)");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			int count;
			while ((count = in.read(chunk)) > 0)
				out.write(chunk, 0, count);
			return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		}
	}

	/**
	 * Writes a fixture back into the source tree.
	 *
	 * @param fileName fixture file name
	 * @param content  fixture content
	 */
	private static void writeFixture(String fileName, String content) {
		try {
			Path directory = FIXTURE_SOURCE_DIRECTORY;
			if (!directory.isAbsolute()) {
				// Surefire runs with a working directory under target, so resolve against the project root.
				directory = Paths.get(System.getProperty("basedir", "")).resolve(directory);
			}
			Files.createDirectories(directory);
			Files.write(directory.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Asserts that {@code actual} matches the named fixture, or rewrites the fixture if regenerating.
	 *
	 * @param fileName fixture file name
	 * @param actual   content produced by this run
	 * @throws IOException if the fixture cannot be read
	 */
	private static void assertMatchesFixture(String fileName, String actual) throws IOException {
		if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
			writeFixture(fileName, actual);
			return;
		}
		assertEquals(readFixture(fileName), actual, "output changed for " + fileName);
		return;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("corpus")
	public void moduleGridMatchesFixture(String name, QrCode qr) throws IOException {
		assertMatchesFixture(name + ".txt", render(qr));
		return;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("corpus")
	public void svgMatchesFixture(String name, QrCode qr) throws IOException {
		assertMatchesFixture(name + ".svg", qr.toSvg(4, "#FFFFFF", "#000000"));
		return;
	}

	/**
	 * <p>
	 * Both {@code toSvg()} overloads document that the result "always uses Unix newlines (\n), regardless of the
	 * platform", so the output must contain no carriage return anywhere.
	 * </p>
	 *
	 * <p>
	 * Note that this only bites on a platform where {@code line.separator} is not {@code \n}: the way to get it
	 * wrong is {@code String.format("...%n")}, which resolves to {@code \n} on Linux and macOS and so looks
	 * correct there. The Windows job in CI is what makes this assertion, and the {@code .svg} fixtures above,
	 * actually load-bearing.
	 * </p>
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("corpus")
	public void svgUsesUnixNewlinesOnly(String name, QrCode qr) {
		for (String svg : new String[] { qr.toSvg(4, "#FFFFFF", "#000000"), qr.toSvg(4, "#FFFFFF", "#000000", true) }) {
			assertFalse(svg.contains("\r"), "SVG for " + name + " contains a carriage return");
		}
		return;
	}

	@Test
	public void svgEmitsXmlDeclarationOnlyWhenRequested() {
		QrCode qr = QrCode.encodeText("Hello, world!", Ecc.LOW);
		String withDeclaration = qr.toSvg(4, "#FFFFFF", "#000000", true);
		String withoutDeclaration = qr.toSvg(4, "#FFFFFF", "#000000", false);
		assertTrue(withDeclaration.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
		assertTrue(withoutDeclaration.startsWith("<svg "));
		assertEquals(withDeclaration.substring("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".length()), withoutDeclaration);
		// The three-argument overload delegates with xmlDeclaration = false.
		assertEquals(withoutDeclaration, qr.toSvg(4, "#FFFFFF", "#000000"));
		return;
	}

	/**
	 * {@link QrCode#toImage(int, int, int, int)} should be a faithful scaled and bordered rendering of the module
	 * grid. Checking that as a property is both cheaper and stricter than pinning the pixels.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("corpus")
	public void imageIsScaledRenderingOfModuleGrid(String name, QrCode qr) {
		int scale = 3;
		int border = 2;
		int light = 0xFFFFE0;
		int dark = 0x303080;
		BufferedImage image = qr.toImage(scale, border, light, dark);
		assertEquals((qr.size + border * 2) * scale, image.getWidth());
		assertEquals((qr.size + border * 2) * scale, image.getHeight());
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				boolean isDark = qr.getModule(x / scale - border, y / scale - border);
				assertEquals(isDark ? dark : light, image.getRGB(x, y) & 0xFFFFFF,
						"pixel (" + x + ", " + y + ") of " + name);
			}
		}
		return;
	}

	/**
	 * The rendered grid must be square and match the version-derived size, which guards the fixtures themselves
	 * against being silently truncated.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("corpus")
	public void renderedGridHasExpectedDimensions(String name, QrCode qr) {
		List<String> rows = Arrays.asList(render(qr).split("\n"));
		assertEquals(qr.version * 4 + 17, qr.size);
		assertEquals(qr.size, rows.size());
		for (String row : rows)
			assertEquals(qr.size, row.length());
		return;
	}
}
