package net.logicsquad.qr4j;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * A QR Code symbol, which is a type of two-dimension barcode. Invented by Denso Wave and described in the
 * ISO/IEC 18004 standard.
 * </p>
 * 
 * <p>
 * Instances of this class represent an immutable square grid of dark and light cells. The class provides
 * static factory functions to create a QR Code from text or binary data. The class covers the QR Code Model 2
 * specification, supporting all versions (sizes) from 1 to 40, all 4 error correction levels, and 4 character
 * encoding modes.
 * </p>
 * 
 * <p>
 * Ways to create a QR Code object:
 * </p>
 * <ul>
 * <li>High level: Take the payload data and call {@link QrCode#encodeText(String,Ecc)} or
 * {@link QrCode#encodeBinary(byte[],Ecc)}.</li>
 * <li>Mid level: Custom-make the list of {@link QrSegment segments} and call
 * {@link QrCode#encodeSegments(List,Ecc)} or {@link QrCode#encodeSegments(List,Ecc,int,int,int,boolean)}</li>
 * <li>Low level: Custom-make the array of data codeword bytes (including segment headers and final padding,
 * excluding error correction codewords), supply the appropriate version number, and call the
 * {@link QrCode#QrCode(int,Ecc,byte[],int) constructor}.</li>
 * </ul>
 * <p>
 * (Note that all ways require supplying the desired error correction level.)
 * </p>
 * 
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
public final class QrCode {
	/**
	 * The minimum version number (1) supported in the QR Code Model 2 standard.
	 */
	public static final int MIN_VERSION =  1;
	
	/**
	 * The maximum version number (40) supported in the QR Code Model 2 standard.
	 */
	public static final int MAX_VERSION = 40;

	private static final int PENALTY_N1 =  3;
	private static final int PENALTY_N2 =  3;
	private static final int PENALTY_N3 = 40;
	private static final int PENALTY_N4 = 10;
	
	/**
	 * ECC codewords per block for version number and ECC level
	 */
	private static final byte[][] ECC_CODEWORDS_PER_BLOCK = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Low
		{-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},  // Medium
		{-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // Quartile
		{-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},  // High
	};

	/**
	 * Number of error correction blocks for version number and ECC level
	 */
	private static final byte[][] NUM_ERROR_CORRECTION_BLOCKS = {
		// Version: (note that index 0 is for padding, and is set to an illegal value)
		//0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
		{-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4,  4,  4,  4,  4,  6,  6,  6,  6,  7,  8,  8,  9,  9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},  // Low
		{-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5,  5,  8,  9,  9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},  // Medium
		{-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8,  8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},  // Quartile
		{-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},  // High
	};
	
	/**
	 * The version number of this QR Code, which is between 1 and 40 (inclusive). This determines the size of this
	 * barcode.
	 */
	public final int version;

	/**
	 * The width and height of this QR Code, measured in modules, between 21 and 177 (inclusive). This is equal to
	 * version &#xD7; 4 + 17.
	 */
	public final int size;

	/**
	 * The error correction level used in this QR Code, which is not {@code null}.
	 */
	public final Ecc errorCorrectionLevel;

	/**
	 * <p>
	 * The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
	 * </p>
	 * <p>
	 * Even if a QR Code is created with automatic masking requested (mask = &#x2212;1), the resulting object
	 * still has a mask value between 0 and 7.
	 * </p>
	 */
	public final int mask;

	/**
	 * Grid of modules of this QR Code, packed tightly into bits. Immutable after constructor finishes. Accessed
	 * through {@link #getModule(int, int)}.
	 */
	private final int[] modules;

	/**
	 * Represents the error correction level in a QR Code symbol.
	 */
	public enum Ecc {
		// Must be declared in ascending order of error protection
		// so that the implicit ordinal() and values() work properly
		/** The QR Code can tolerate about  7% erroneous codewords. */ LOW(1),
		/** The QR Code can tolerate about 15% erroneous codewords. */ MEDIUM(0),
		/** The QR Code can tolerate about 25% erroneous codewords. */ QUARTILE(3),
		/** The QR Code can tolerate about 30% erroneous codewords. */ HIGH(2);

		/**
		 * Format bits: in the range 0 to 3 (unsigned 2-bit integer)
		 */
		final int formatBits;

		/**
		 * Constructor
		 * 
		 * @param fb format bits
		 */
		private Ecc(int fb) {
			formatBits = fb;
		}
	}

	/**
	 * <p>
	 * Constructs a QR Code with the specified version number, error correction level, data codeword bytes, and
	 * mask number.
	 * </p>
	 * <p>
	 * This is a low-level API that most users should not use directly. A mid-level API is the
	 * {@link #encodeSegments(List,Ecc,int,int,int,boolean)} function.
	 * </p>
	 * 
	 * @param ver           the version number to use, which must be in the range 1 to 40 (inclusive)
	 * @param ecl           the error correction level to use
	 * @param dataCodewords the bytes representing segments to encode (without ECC)
	 * @param msk           the mask pattern to use, which is either &#x2212;1 for automatic choice or from 0 to 7
	 *                      for fixed choice
	 * @throws NullPointerException     if the byte array or error correction level is {@code null}
	 * @throws IllegalArgumentException if the version or mask value is out of range, or if the data is the wrong
	 *                                  length for the specified version and error correction level
	 */
	public QrCode(int ver, Ecc ecl, byte[] dataCodewords, int msk) {
		// Check arguments and initialize fields
		if (ver < MIN_VERSION || ver > MAX_VERSION)
			throw new IllegalArgumentException("Version value out of range");
		if (msk < -1 || msk > 7)
			throw new IllegalArgumentException("Mask value out of range");
		version = ver;
		size = ver * 4 + 17;
		errorCorrectionLevel = Objects.requireNonNull(ecl);
		Objects.requireNonNull(dataCodewords);
		
		Template tpl = Template.MEMOIZER.get(ver);
		modules = tpl.template.clone();
		
		// Compute ECC, draw modules, do masking
		byte[] allCodewords = addEccAndInterleave(dataCodewords);
		drawCodewords(tpl.dataOutputBitIndexes, allCodewords);
		mask = handleConstructorMasking(tpl.masks, msk);
	}

	/**
	 * Returns a QR Code representing the specified Unicode text string at the specified error correction level.
	 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
	 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest
	 * possible QR Code version is automatically chosen for the output. The ECC level of the result may be higher
	 * than the ecl argument if it can be done without increasing the version.
	 * 
	 * @param text the text to be encoded (not {@code null}), which can be any Unicode string
	 * @param ecl  the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the text
	 * @throws NullPointerException if the text or error correction level is {@code null}
	 * @throws DataTooLongException if the text fails to fit in the largest version QR Code at the ECL, which
	 *                              means it is too long
	 */
	public static QrCode encodeText(String text, Ecc ecl) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(ecl);
		List<QrSegment> segs = QrSegment.makeSegments(text);
		return encodeSegments(segs, ecl);
	}

	/**
	 * Returns a QR Code representing the specified binary data at the specified error correction level. This
	 * function always encodes using the binary segment mode, not any text mode. The maximum number of bytes
	 * allowed is 2953. The smallest possible QR Code version is automatically chosen for the output. The ECC
	 * level of the result may be higher than the ecl argument if it can be done without increasing the version.
	 * 
	 * @param data the binary data to encode (not {@code null})
	 * @param ecl  the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the data
	 * @throws NullPointerException if the data or error correction level is {@code null}
	 * @throws DataTooLongException if the data fails to fit in the largest version QR Code at the ECL, which
	 *                              means it is too long
	 */
	public static QrCode encodeBinary(byte[] data, Ecc ecl) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(ecl);
		QrSegment seg = QrSegment.makeBytes(data);
		return encodeSegments(Arrays.asList(seg), ecl);
	}

	/**
	 * <p>
	 * Returns a QR Code representing the specified segments at the specified error correction level. The smallest
	 * possible QR Code version is automatically chosen for the output. The ECC level of the result may be higher
	 * than the ecl argument if it can be done without increasing the version.
	 * </p>
	 * <p>
	 * This function allows the user to create a custom sequence of segments that switches between modes (such as
	 * alphanumeric and byte) to encode text in less space. This is a mid-level API; the high-level API is
	 * {@link #encodeText(String,Ecc)} and {@link #encodeBinary(byte[],Ecc)}.
	 * </p>
	 * 
	 * @param segs the segments to encode
	 * @param ecl  the error correction level to use (not {@code null}) (boostable)
	 * @return a QR Code (not {@code null}) representing the segments
	 * @throws NullPointerException if the list of segments, any segment, or the error correction level is
	 *                              {@code null}
	 * @throws DataTooLongException if the segments fail to fit in the largest version QR Code at the ECL, which
	 *                              means they are too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl) {
		return encodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, true);
	}

	/**
	 * <p>
	 * Returns a QR Code representing the specified segments with the specified encoding parameters. The smallest
	 * possible QR Code version within the specified range is automatically chosen for the output. Iff boostEcl is
	 * {@code true}, then the ECC level of the result may be higher than the ecl argument if it can be done
	 * without increasing the version. The mask number is either between 0 to 7 (inclusive) to force that mask, or
	 * &#x2212;1 to automatically choose an appropriate mask (which may be slow).
	 * </p>
	 * <p>
	 * This function allows the user to create a custom sequence of segments that switches between modes (such as
	 * alphanumeric and byte) to encode text in less space. This is a mid-level API; the high-level API is
	 * {@link #encodeText(String,Ecc)} and {@link #encodeBinary(byte[],Ecc)}.
	 * </p>
	 * 
	 * @param segs       the segments to encode
	 * @param ecl        the error correction level to use (not {@code null}) (boostable)
	 * @param minVersion the minimum allowed version of the QR Code (at least 1)
	 * @param maxVersion the maximum allowed version of the QR Code (at most 40)
	 * @param mask       the mask number to use (between 0 and 7 (inclusive)), or &#x2212;1 for automatic mask
	 * @param boostEcl   increases the ECC level as long as it doesn't increase the version number
	 * @return a QR Code (not {@code null}) representing the segments
	 * @throws NullPointerException     if the list of segments, any segment, or the error correction level is
	 *                                  {@code null}
	 * @throws IllegalArgumentException if 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40 or &#x2212;1
	 *                                  &#x2264; mask &#x2264; 7 is violated
	 * @throws DataTooLongException     if the segments fail to fit in the maxVersion QR Code at the ECL, which
	 *                                  means they are too long
	 */
	public static QrCode encodeSegments(List<QrSegment> segs, Ecc ecl, int minVersion, int maxVersion, int mask, boolean boostEcl) {
		Objects.requireNonNull(segs);
		Objects.requireNonNull(ecl);
		if (!(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION) || mask < -1 || mask > 7)
			throw new IllegalArgumentException("Invalid value");
		
		// Find the minimal version number to use
		int version, dataUsedBits;
		for (version = minVersion; ; version++) {
			int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;  // Number of data bits available
			dataUsedBits = QrSegment.getTotalBits(segs, version);
			if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits)
				break;  // This version number is found to be suitable
			if (version >= maxVersion) {  // All versions in the range could not fit the given data
				String msg = "Segment too long";
				if (dataUsedBits != -1)
					msg = String.format("Data length = %d bits, Max capacity = %d bits", dataUsedBits, dataCapacityBits);
				throw new DataTooLongException(msg);
			}
		}
		assert dataUsedBits != -1;
		
		// Increase the error correction level while the data still fits in the current version number
		for (Ecc newEcl : Ecc.values()) {  // From low to high
			if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8)
				ecl = newEcl;
		}
		
		// Concatenate all segments to create the data bit string
		BitBuffer bb = new BitBuffer();
		for (QrSegment seg : segs) {
			bb.appendBits(seg.mode.modeBits, 4);
			bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version));
			bb.appendBits(seg.data, seg.bitLength);
		}
		assert bb.bitLength == dataUsedBits;
		
		// Add terminator and pad up to a byte if applicable
		int dataCapacityBits = getNumDataCodewords(version, ecl) * 8;
		assert bb.bitLength <= dataCapacityBits;
		bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength));
		bb.appendBits(0, (8 - bb.bitLength % 8) % 8);
		assert bb.bitLength % 8 == 0;
		
		// Pad with alternating bytes until data capacity is reached
		for (int padByte = 0xEC; bb.bitLength < dataCapacityBits; padByte ^= 0xEC ^ 0x11)
			bb.appendBits(padByte, 8);
		
		// Create the QR Code object
		return new QrCode(version, ecl, bb.getBytes(), mask);
	}

	/**
	 * Returns the color of the module (pixel) at the specified coordinates, which is {@code false} for light or
	 * {@code true} for dark. The top left corner has the coordinates (x=0, y=0). If the specified coordinates are
	 * out of bounds, then {@code false} (light) is returned.
	 * 
	 * @param x the x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge
	 * @param y the y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge
	 * @return {@code true} if the coordinates are in bounds and the module at that location is dark, or
	 *         {@code false} (light) otherwise
	 */
	public boolean getModule(int x, int y) {
		if (0 <= x && x < size && 0 <= y && y < size) {
			int i = y * size + x;
			return getBit(modules[i >>> 5], i) != 0;
		} else
			return false;
	}

	/**
	 * Draws two copies of the format bits (with its own error correction code) based on the given mask and this
	 * object's error correction level field.
	 * 
	 * @param msk mask
	 */
	private void drawFormatBits(int msk) {
		// Calculate error correction code and pack bits
		int data = errorCorrectionLevel.formatBits << 3 | msk;  // errCorrLvl is uint2, mask is uint3
		int rem = data;
		for (int i = 0; i < 10; i++)
			rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
		int bits = (data << 10 | rem) ^ 0x5412;  // uint15
		assert bits >>> 15 == 0;
		
		// Draw first copy
		for (int i = 0; i <= 5; i++)
			setModule(8, i, getBit(bits, i));
		setModule(8, 7, getBit(bits, 6));
		setModule(8, 8, getBit(bits, 7));
		setModule(7, 8, getBit(bits, 8));
		for (int i = 9; i < 15; i++)
			setModule(14 - i, 8, getBit(bits, i));
		
		// Draw second copy
		for (int i = 0; i < 8; i++)
			setModule(size - 1 - i, 8, getBit(bits, i));
		for (int i = 8; i < 15; i++)
			setModule(8, size - 15 + i, getBit(bits, i));
		setModule(8, size - 8, 1);  // Always dark
	}

	/**
	 * Sets the module at the given coordinates to the given color. Only used by the constructor. Coordinates must
	 * be in bounds.
	 * 
	 * @param x x co-ordinate
	 * @param y y co-ordinate
	 * @param colour 0 for light or 1 for dark
	 */
	private void setModule(int x, int y, int colour) {
		assert 0 <= x && x < size;
		assert 0 <= y && y < size;
		assert colour == 0 || colour == 1;
		int i = y * size + x;
		modules[i >>> 5] &= ~(1 << i);
		modules[i >>> 5] |= colour << i;
	}

	/**
	 * Returns a new byte string representing {@code data} with the appropriate error correction codewords
	 * appended to it, based on this object's version and error correction level.
	 * 
	 * @param data data
	 * @return data with error correction codewords appended
	 * @throws IllegalArgumentException if {@code data} is the wrong length for {@link #version} and
	 *                                  {@link #errorCorrectionLevel}
	 */
	private byte[] addEccAndInterleave(byte[] data) {
		Objects.requireNonNull(data);
		if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
			throw new IllegalArgumentException();
		
		// Calculate parameter numbers
		int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
		int blockEccLen = ECC_CODEWORDS_PER_BLOCK  [errorCorrectionLevel.ordinal()][version];
		int rawCodewords = Template.getNumRawDataModules(version) / 8;
		int numShortBlocks = numBlocks - rawCodewords % numBlocks;
		int shortBlockDataLen = rawCodewords / numBlocks - blockEccLen;
		
		// Split data into blocks, calculate ECC, and interleave
		// (not concatenate) the bytes into a single sequence
		byte[] result = new byte[rawCodewords];
		ReedSolomonGenerator rs = ReedSolomonGenerator.MEMOIZER.get(blockEccLen);
		byte[] ecc = new byte[blockEccLen];  // Temporary storage per iteration
		for (int i = 0, k = 0; i < numBlocks; i++) {
			int datLen = shortBlockDataLen + (i < numShortBlocks ? 0 : 1);
			rs.getRemainder(data, k, datLen, ecc);
			for (int j = 0, l = i; j < datLen; j++, k++, l += numBlocks) {  // Copy data
				if (j == shortBlockDataLen)
					l -= numShortBlocks;
				result[l] = data[k];
			}
			for (int j = 0, l = data.length + i; j < blockEccLen; j++, l += numBlocks)  // Copy ECC
				result[l] = ecc[j];
		}
		return result;
	}

	/**
	 * Draws the given sequence of 8-bit codewords (data and error correction)
	 * onto the entire data area of this QR Code, based on the given bit indexes.
	 * 
	 * @param dataOutputBitIndexes
	 * @param allCodewords
	 */
	private void drawCodewords(int[] dataOutputBitIndexes, byte[] allCodewords) {
		Objects.requireNonNull(dataOutputBitIndexes);
		Objects.requireNonNull(allCodewords);
		if (allCodewords.length * 8 != dataOutputBitIndexes.length)
			throw new IllegalArgumentException();
		for (int i = 0; i < dataOutputBitIndexes.length; i++) {
			int j = dataOutputBitIndexes[i];
			int bit = getBit(allCodewords[i >>> 3], ~i & 7);
			modules[j >>> 5] |= bit << j;
		}
	}

	/**
	 * XORs the codeword modules in this QR Code with the given mask pattern. The function modules must be marked
	 * and the codeword bits must be drawn before masking. Due to the arithmetic of XOR, calling applyMask() with
	 * the same mask value a second time will undo the mask. A final well-formed QR Code needs exactly one (not
	 * zero, two, etc.) masks applied.
	 * 
	 * @param msk mask
	 * @throws IllegalArgumentException if {@code msk} and {@link #modules} are not the same size
	 */
	private void applyMask(int[] msk) {
		if (msk.length != modules.length)
			throw new IllegalArgumentException();
		for (int i = 0; i < msk.length; i++)
			modules[i] ^= msk[i];
	}

	/**
	 * Applies and returns a mask to this object. {@code msk} is the requested mask, which is -1 for auto or 0 to
	 * 7 for fixed. This method applies and returns the actual mask chosen, from 0 to 7. This object must be in an
	 * unmasked state when this method is called.
	 * 
	 * @param masks candidate masks
	 * @param msk   requested mask (or -1 for automatic)
	 * @return mask applied
	 */
	private int handleConstructorMasking(int[][] masks, int msk) {
		if (msk == -1) {  // Automatically choose best mask
			int minPenalty = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				applyMask(masks[i]);
				drawFormatBits(i);
				int penalty = getPenaltyScore();
				if (penalty < minPenalty) {
					msk = i;
					minPenalty = penalty;
				}
				applyMask(masks[i]);  // Undoes the mask due to XOR
			}
		}
		assert 0 <= msk && msk <= 7;
		applyMask(masks[msk]);  // Apply the final choice of mask
		drawFormatBits(msk);  // Overwrite old format bits
		return msk;  // The caller shall assign this value to the final-declared field
	}

	/**
	 * Calculates and returns the penalty score based on state of this object's current modules. This is used by
	 * the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
	 * 
	 * @return penalty score
	 */
	private int getPenaltyScore() {
		int result = 0;
		int dark = 0;
		int[] runHistory = new int[7];
		
		// Iterate over adjacent pairs of rows
		for (int index = 0, downIndex = size, end = size * size; index < end; ) {
			int runColor = 0;
			int runX = 0;
			Arrays.fill(runHistory, 0);
			int curRow = 0;
			int nextRow = 0;
			for (int x = 0; x < size; x++, index++, downIndex++) {
				int c = getBit(modules[index >>> 5], index);
				if (c == runColor) {
					runX++;
					if (runX == 5)
						result += PENALTY_N1;
					else if (runX > 5)
						result++;
				} else {
					finderPenaltyAddHistory(runX, runHistory);
					if (runColor == 0)
						result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
					runColor = c;
					runX = 1;
				}
				dark += c;
				if (downIndex < end) {
					curRow = ((curRow << 1) | c) & 3;
					nextRow = ((nextRow << 1) | getBit(modules[downIndex >>> 5], downIndex)) & 3;
					// 2*2 blocks of modules having same color
					if (x >= 1 && (curRow == 0 || curRow == 3) && curRow == nextRow)
						result += PENALTY_N2;
				}
			}
			result += finderPenaltyTerminateAndCount(runColor, runX, runHistory) * PENALTY_N3;
		}
		
		// Iterate over single columns
		for (int x = 0; x < size; x++) {
			int runColor = 0;
			int runY = 0;
			Arrays.fill(runHistory, 0);
			for (int y = 0, index = x; y < size; y++, index += size) {
				int c = getBit(modules[index >>> 5], index);
				if (c == runColor) {
					runY++;
					if (runY == 5)
						result += PENALTY_N1;
					else if (runY > 5)
						result++;
				} else {
					finderPenaltyAddHistory(runY, runHistory);
					if (runColor == 0)
						result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
					runColor = c;
					runY = 1;
				}
			}
			result += finderPenaltyTerminateAndCount(runColor, runY, runHistory) * PENALTY_N3;
		}
		
		// Balance of dark and light modules
		int total = size * size;  // Note that size is odd, so dark/total != 1/2
		// Compute the smallest integer k >= 0 such that (45-5k)% <= dark/total <= (55+5k)%
		int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
		result += k * PENALTY_N4;
		return result;
	}

	/**
	 * Returns the number of 8-bit data (i.e. not error correction) codewords contained in any
	 * QR Code of the given version number and error correction level, with remainder bits discarded.
	 * This stateless pure function could be implemented as a (40*4)-cell lookup table.
	 * 
	 * @param ver version
	 * @param ecl error correction level
	 * @return number of data codewords
	 */
	static int getNumDataCodewords(int ver, Ecc ecl) {
		return Template.getNumRawDataModules(ver) / 8
			- ECC_CODEWORDS_PER_BLOCK    [ecl.ordinal()][ver]
			* NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
	}

	/**
	 * Can only be called immediately after a light run is added, and returns either 0, 1, or 2. A helper function
	 * for {@link #getPenaltyScore()}.
	 * 
	 * @param runHistory
	 * @return
	 */
	private int finderPenaltyCountPatterns(int[] runHistory) {
		int n = runHistory[1];
		assert n <= size * 3;
		boolean core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n;
		return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0) + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
	}	

	/**
	 * Must be called at the end of a line (row or column) of modules. A helper function for
	 * {@link #getPenaltyScore()}.
	 * 
	 * @param currentRunColor
	 * @param currentRunLength
	 * @param runHistory
	 * @return
	 */
	private int finderPenaltyTerminateAndCount(int currentRunColor, int currentRunLength, int[] runHistory) {
		if (currentRunColor == 1) { // Terminate dark run
			finderPenaltyAddHistory(currentRunLength, runHistory);
			currentRunLength = 0;
		}
		currentRunLength += size; // Add light border to final run
		finderPenaltyAddHistory(currentRunLength, runHistory);
		return finderPenaltyCountPatterns(runHistory);
	}	

	/**
	 * Pushes the given value to the front and drops the last value. A helper function for
	 * {@link #getPenaltyScore()}.
	 * 
	 * @param currentRunLength
	 * @param runHistory
	 */
	private void finderPenaltyAddHistory(int currentRunLength, int[] runHistory) {
		if (runHistory[0] == 0)
			currentRunLength += size; // Add light border to initial run
		System.arraycopy(runHistory, 0, runHistory, 1, runHistory.length - 1);
		runHistory[0] = currentRunLength;
	}

	/**
	 * Returns 0 or 1 based on the {@code (i mod 32)}th bit of x.
	 * 
	 * @param x
	 * @param i
	 * @return
	 */
	private static int getBit(int x, int i) {
		return (x >>> i) & 1;
	}

	/**
	 * Returns a {@link BufferedImage} depicting this object.
	 * 
	 * @param scale  the side length (measured in pixels, must be positive) of each module
	 * @param border the number of border modules to add, which must be non-negative
	 * @return a new image representing this object, with padding and scaling
	 * @see #toImage(int, int, int, int)
	 */
	public BufferedImage toImage(int scale, int border) {
		return toImage(scale, border, 0xFFFFFF, 0x000000);
	}
	
	/**
	 * <p>
	 * Returns a {@link BufferedImage} depicting this object, with the specified module scale, border modules, and
	 * module colors.
	 * </p>
	 * <p>
	 * For example, scale=10 and border=4 means to pad the QR Code with 4 light border modules on all four sides,
	 * and use 10&#xD7;10 pixels to represent each module.
	 * </p>
	 * 
	 * @param scale      the side length (measured in pixels, must be positive) of each module
	 * @param border     the number of border modules to add, which must be non-negative
	 * @param lightColor the color to use for light modules, in 0xRRGGBB format
	 * @param darkColor  the color to use for dark modules, in 0xRRGGBB format
	 * @return a new image representing the QR Code, with padding and scaling
	 * @throws IllegalArgumentException if the scale or border is out of range, or if {scale, border, size} cause
	 *                                  the image dimensions to exceed Integer.MAX_VALUE
	 * @see #toImage(int, int)
	 */
	public BufferedImage toImage(int scale, int border, int lightColor, int darkColor) {
		if (scale <= 0 || border < 0)
			throw new IllegalArgumentException("Value out of range");
		if (border > Integer.MAX_VALUE / 2 || size + border * 2L > Integer.MAX_VALUE / scale)
			throw new IllegalArgumentException("Scale or border too large");
		
		BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				boolean color = getModule(x / scale - border, y / scale - border);
				result.setRGB(x, y, color ? darkColor : lightColor);
			}
		}
		return result;
	}

	/**
	 * Returns a string of SVG code for an image depicting this object, with the specified number of border
	 * modules. The string always uses Unix newlines (\n), regardless of the platform.
	 * 
	 * @param border     the number of border modules to add, which must be non-negative
	 * @param lightColor the color to use for light modules, in any format supported by CSS, not {@code null}
	 * @param darkColor  the color to use for dark modules, in any format supported by CSS, not {@code null}
	 * @return a string representing the QR Code as an SVG XML document
	 * @throws NullPointerException     if any argument is {@code null}
	 * @throws IllegalArgumentException if the border is negative
	 */
	public String toSvgString(int border, String lightColor, String darkColor) {
		Objects.requireNonNull(lightColor);
		Objects.requireNonNull(darkColor);
		if (border < 0)
			throw new IllegalArgumentException("Border must be non-negative");
		long brd = border;
		StringBuilder sb = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
			.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
				size + brd * 2))
			.append("\t<rect width=\"100%\" height=\"100%\" fill=\"" + lightColor + "\"/>\n")
			.append("\t<path d=\"");
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (getModule(x, y)) {
					if (x != 0 || y != 0)
						sb.append(" ");
					sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
				}
			}
		}
		return sb
			.append("\" fill=\"" + darkColor + "\"/>\n")
			.append("</svg>\n")
			.toString();
	}

	/**
	 * Stores the parts of a QR Code that depend only on the version number, and does not depend on the data or
	 * error correction level or mask.
	 * 
	 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
	 */
	private static final class Template {
		/**
		 * {@link Memoizer} holding instances of this class
		 */
		public static final Memoizer<Integer, Template> MEMOIZER = new Memoizer<>(Template::new);		

		/**
		 * Version number (in the range {@code [1, 40]})
		 */
		private final int version;

		/**
		 * QR Code size, derived from version
		 */
		private final int size;  // Derived from version.
		
		final int[] template;  // Length and values depend on version.
		final int[][] masks;  // masks.length == 8, and masks[i].length == template.length.
		final int[] dataOutputBitIndexes;  // Length and values depend on version.
		
		// Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
		// Otherwise when the constructor is running, isFunction.length == template.length.
		private int[] isFunction;

		/**
		 * Constructor taking a version number
		 * 
		 * @param ver QR Code version
		 */
		private Template(int ver) {
			if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
				throw new IllegalArgumentException("Version out of range");
			version = ver;
			size = version * 4 + 17;
			template = new int[(size * size + 31) / 32];
			isFunction = new int[template.length];
			
			drawFunctionPatterns();  // Reads and writes fields
			masks = generateMasks();  // Reads fields, returns array
			dataOutputBitIndexes = generateZigzagScan();  // Reads fields, returns array
			isFunction = null;
		}

		/**
		 * Reads this object's version field, and draws and marks all function modules.
		 */
		private void drawFunctionPatterns() {
			// Draw horizontal and vertical timing patterns
			for (int i = 0; i < size; i++) {
				darkenFunctionModule(6, i, ~i & 1);
				darkenFunctionModule(i, 6, ~i & 1);
			}
			
			// Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
			drawFinderPattern(3, 3);
			drawFinderPattern(size - 4, 3);
			drawFinderPattern(3, size - 4);
			
			// Draw numerous alignment patterns
			int[] alignPatPos = getAlignmentPatternPositions();
			int numAlign = alignPatPos.length;
			for (int i = 0; i < numAlign; i++) {
				for (int j = 0; j < numAlign; j++) {
					// Don't draw on the three finder corners
					if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
						drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
				}
			}
			
			// Draw configuration data
			drawDummyFormatBits();
			drawVersion();
		}

		/**
		 * Draws two blank copies of the format bits.
		 */
		private void drawDummyFormatBits() {
			// Draw first copy
			for (int i = 0; i <= 5; i++)
				darkenFunctionModule(8, i, 0);
			darkenFunctionModule(8, 7, 0);
			darkenFunctionModule(8, 8, 0);
			darkenFunctionModule(7, 8, 0);
			for (int i = 9; i < 15; i++)
				darkenFunctionModule(14 - i, 8, 0);
			
			// Draw second copy
			for (int i = 0; i < 8; i++)
				darkenFunctionModule(size - 1 - i, 8, 0);
			for (int i = 8; i < 15; i++)
				darkenFunctionModule(8, size - 15 + i, 0);
			darkenFunctionModule(8, size - 8, 1);  // Always dark
		}

		/**
		 * Draws two copies of the version bits (with its own error correction code),
		 * based on this object's version field, iff 7 <= version <= 40.
		 */
		private void drawVersion() {
			if (version < 7)
				return;
			
			// Calculate error correction code and pack bits
			int rem = version;  // version is uint6, in the range [7, 40]
			for (int i = 0; i < 12; i++)
				rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
			int bits = version << 12 | rem;  // uint18
			assert bits >>> 18 == 0;
			
			// Draw two copies
			for (int i = 0; i < 18; i++) {
				int bit = QrCode.getBit(bits, i);
				int a = size - 11 + i % 3;
				int b = i / 3;
				darkenFunctionModule(a, b, bit);
				darkenFunctionModule(b, a, bit);
			}
		}

		/**
		 * Draws a 9*9 finder pattern including the border separator, with the center module at (x, y). Modules can be
		 * out of bounds.
		 * 
		 * @param x x co-ordinate
		 * @param y y co-ordinate
		 */
		private void drawFinderPattern(int x, int y) {
			for (int dy = -4; dy <= 4; dy++) {
				for (int dx = -4; dx <= 4; dx++) {
					int dist = Math.max(Math.abs(dx), Math.abs(dy));  // Chebyshev/infinity norm
					int xx = x + dx, yy = y + dy;
					if (0 <= xx && xx < size && 0 <= yy && yy < size)
						darkenFunctionModule(xx, yy, (dist != 2 && dist != 4) ? 1 : 0);
				}
			}
		}

		/**
		 * Draws a 5*5 alignment pattern, with the center module at (x, y). All modules must be in bounds.
		 * 
		 * @param x x co-ordinate
		 * @param y y co-ordinate
		 */
		private void drawAlignmentPattern(int x, int y) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dx = -2; dx <= 2; dx++)
					darkenFunctionModule(x + dx, y + dy, Math.abs(Math.max(Math.abs(dx), Math.abs(dy)) - 1));
			}
		}

		/**
		 * Computes and returns a new array of masks, based on this object's various fields.
		 * 
		 * @return array of masks
		 */
		private int[][] generateMasks() {
			int[][] result = new int[8][template.length];
			for (int mask = 0; mask < result.length; mask++) {
				int[] maskModules = result[mask];
				for (int y = 0, i = 0; y < size; y++) {
					for (int x = 0; x < size; x++, i++) {
						boolean invert;
						switch (mask) {
							case 0:  invert = (x + y) % 2 == 0;                    break;
							case 1:  invert = y % 2 == 0;                          break;
							case 2:  invert = x % 3 == 0;                          break;
							case 3:  invert = (x + y) % 3 == 0;                    break;
							case 4:  invert = (x / 3 + y / 2) % 2 == 0;            break;
							case 5:  invert = x * y % 2 + x * y % 3 == 0;          break;
							case 6:  invert = (x * y % 2 + x * y % 3) % 2 == 0;    break;
							case 7:  invert = ((x + y) % 2 + x * y % 3) % 2 == 0;  break;
							default:  throw new AssertionError();
						}
						int bit = (invert ? 1 : 0) & ~getModule(isFunction, x, y);
						maskModules[i >>> 5] |= bit << i;
					}
				}
			}
			return result;
		}

		/**
		 * Computes and returns an array of bit indexes, based on this object's various fields.
		 * 
		 * @return array of bit indexes
		 */
		private int[] generateZigzagScan() {
			int[] result = new int[getNumRawDataModules(version) / 8 * 8];
			int i = 0;  // Bit index into the data
			for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
				if (right == 6)
					right = 5;
				for (int vert = 0; vert < size; vert++) {  // Vertical counter
					for (int j = 0; j < 2; j++) {
						int x = right - j;  // Actual x coordinate
						boolean upward = ((right + 1) & 2) == 0;
						int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
						if (getModule(isFunction, x, y) == 0 && i < result.length) {
							result[i] = y * size + x;
							i++;
						}
					}
				}
			}
			assert i == result.length;
			return result;
		}

		/**
		 * Returns the value of the bit at the given coordinates in {@code grid}.
		 * 
		 * @param grid grid of bit values
		 * @param x    x co-ordinate
		 * @param y    y co-ordinate
		 * @return bit value at (x, y)
		 */
		private int getModule(int[] grid, int x, int y) {
			assert 0 <= x && x < size;
			assert 0 <= y && y < size;
			int i = y * size + x;
			return QrCode.getBit(grid[i >>> 5], i);
		}

		/**
		 * Marks the module at the given coordinates as a function module. Also either sets that module dark or keeps
		 * its color unchanged.
		 * 
		 * @param x      x co-ordinate
		 * @param y      y co-ordinate
		 * @param enable
		 */
		private void darkenFunctionModule(int x, int y, int enable) {
			assert 0 <= x && x < size;
			assert 0 <= y && y < size;
			assert enable == 0 || enable == 1;
			int i = y * size + x;
			template[i >>> 5] |= enable << i;
			isFunction[i >>> 5] |= 1 << i;
		}

		/**
		 * Returns an ascending list of positions of alignment patterns for this version number. Each position is in
		 * the range [0,177), and are used on both the x and y axes. This could be implemented as lookup table of 40
		 * variable-length lists of unsigned bytes.
		 * 
		 * @return alignment pattersn
		 */
		private int[] getAlignmentPatternPositions() {
			if (version == 1)
				return new int[]{};
			else {
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

		/**
		 * Returns the number of data bits that can be stored in a QR Code of the given version number, after all
		 * function modules are excluded. This includes remainder bits, so it might not be a multiple of 8. The result
		 * is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
		 * 
		 * @param ver version number
		 * @return number of data bits
		 */
		static int getNumRawDataModules(int ver) {
			if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION)
				throw new IllegalArgumentException("Version number out of range");
			int result = (16 * ver + 128) * ver + 64;
			if (ver >= 2) {
				int numAlign = ver / 7 + 2;
				result -= (25 * numAlign - 10) * numAlign - 55;
				if (ver >= 7)
					result -= 36;
			}
			return result;
		}
	}
}
