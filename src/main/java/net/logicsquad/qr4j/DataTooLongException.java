package net.logicsquad.qr4j;


/**
 * <p>
 * Thrown when the supplied data does not fit any QR Code version. Ways to handle this exception include:
 * </p>
 * 
 * <ul>
 * <li>Decrease the error correction level if it was greater than {@code Ecc.LOW}.</li>
 * <li>If the advanced {@code encodeSegments()} function with 6 arguments or the
 * {@code makeSegmentsOptimally()} function was called, then increase the maxVersion argument if it was less
 * than {@link QrCode#MAX_VERSION}. (This advice does not apply to the other factory functions because they
 * search all versions up to {@code QrCode.MAX_VERSION}.)</li>
 * <li>Split the text data into better or optimal segments in order to reduce the number of bits required.
 * (See {@link QrSegment.Utility#makeSegmentsOptimally(String, net.logicsquad.qr4j.QrCode.Ecc, int, int)
 * Utility.makeSegmentsOptimally()}.)</li>
 * <li>Change the text or binary data to be shorter.</li>
 * <li>Change the text to fit the character set of a particular segment mode (e.g. alphanumeric).</li>
 * <li>Propagate the error upward to the caller/user.</li>
 * </ul>
 * 
 * @author <a href="mailto:me@nayuki.io">Nayuki</a>
 */
public class DataTooLongException extends IllegalArgumentException {
	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = 1L;


	public DataTooLongException() {}
	
	
	public DataTooLongException(String msg) {
		super(msg);
	}
	
}
