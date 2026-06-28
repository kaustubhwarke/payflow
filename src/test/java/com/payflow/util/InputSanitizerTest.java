package com.payflow.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for {@link InputSanitizer} (AAA pattern).
 */
class InputSanitizerTest {

    @Test
    void sanitize_returnsNullForNull() {
        assertThat(InputSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_trimsSurroundingWhitespace() {
        assertThat(InputSanitizer.sanitize("  Priya  ")).isEqualTo("Priya");
    }

    @Test
    void sanitize_stripsControlCharacters() {
        // Arrange: embed a tab and a bell control character
        String raw = "Pri\tya";

        // Act
        String result = InputSanitizer.sanitize(raw);

        // Assert
        assertThat(result).isEqualTo("Priya");
    }

    @Test
    void sanitize_removesAngleBracketsToDefuseHtml() {
        assertThat(InputSanitizer.sanitize("<script>alert(1)</script>"))
                .isEqualTo("scriptalert(1)/script");
    }

    @Test
    void constructor_isNotInstantiable() throws NoSuchMethodException {
        // Arrange
        Constructor<InputSanitizer> constructor = InputSanitizer.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // Act
        Throwable thrown = catchThrowable(constructor::newInstance);

        // Assert
        assertThat(thrown).isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(AssertionError.class);
    }
}
