package com.payflow.util;

import java.text.Normalizer;

/**
 * Defensive input sanitisation helpers (mentor feedback 22.e: input sanitization beyond
 * bean-validation). Validation rejects structurally invalid input; sanitisation normalises
 * structurally valid input before it is persisted, removing control characters and
 * neutralising stored-XSS vectors.
 *
 * <p>Stateless utility class &mdash; not instantiable.</p>
 */
public final class InputSanitizer {

    private InputSanitizer() {
        throw new AssertionError("InputSanitizer is a utility class and must not be instantiated");
    }

    /**
     * Normalises Unicode, trims surrounding whitespace, strips ASCII/Unicode control
     * characters, and removes angle brackets to defuse stored-HTML/script injection.
     *
     * @param value raw input (nullable)
     * @return the sanitised value, or {@code null} if the input was {@code null}
     */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String stripped = normalized.replaceAll("[\\p{Cntrl}]", "");
        String noTags = stripped.replaceAll("[<>]", "");
        return noTags.trim();
    }
}
