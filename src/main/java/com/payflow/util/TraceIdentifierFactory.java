package com.payflow.util;

import java.security.SecureRandom;

/**
 * Generates W3C Trace-Context compatible identifiers (Rule 13).
 *
 * <ul>
 *     <li><b>traceId</b> &mdash; 32 lowercase hex characters (128-bit), the industry standard
 *     used by OpenTelemetry / W3C {@code traceparent}.</li>
 *     <li><b>spanId</b> &mdash; 16 lowercase hex characters (64-bit).</li>
 * </ul>
 *
 * <p>Both generators reject the all-zero value, which the W3C specification defines as
 * invalid. Stateless utility class &mdash; not instantiable.</p>
 */
public final class TraceIdentifierFactory {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TRACE_ID_BYTES = 16; // 16 bytes -> 32 hex chars
    private static final int SPAN_ID_BYTES = 8;   // 8 bytes  -> 16 hex chars
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TraceIdentifierFactory() {
        throw new AssertionError("TraceIdentifierFactory is a utility class and must not be instantiated");
    }

    /** @return a non-zero 32-hex-character trace identifier. */
    public static String newTraceId() {
        return randomHex(TRACE_ID_BYTES);
    }

    /** @return a non-zero 16-hex-character span identifier. */
    public static String newSpanId() {
        return randomHex(SPAN_ID_BYTES);
    }

    /**
     * Validates that a value is a non-zero lowercase hex string of the expected length.
     *
     * @param value           candidate identifier
     * @param expectedLength   required number of hex characters
     * @return {@code true} if the value is well-formed and non-zero
     */
    public static boolean isValid(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) {
            return false;
        }
        boolean allZero = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) {
                return false;
            }
            if (c != '0') {
                allZero = false;
            }
        }
        return !allZero;
    }

    private static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        // Guarantee non-zero per W3C spec.
        bytes[0] |= 0x01;
        char[] out = new char[numBytes * 2];
        for (int i = 0; i < numBytes; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }
}
