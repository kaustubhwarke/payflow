package com.payflow.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for {@link TraceIdentifierFactory} (AAA pattern).
 */
class TraceIdentifierFactoryTest {

    @Test
    void newTraceId_is32HexCharsAndNonZero() {
        // Act
        String traceId = TraceIdentifierFactory.newTraceId();

        // Assert
        assertThat(traceId).hasSize(32).matches("[0-9a-f]+");
        assertThat(TraceIdentifierFactory.isValid(traceId, 32)).isTrue();
    }

    @Test
    void newSpanId_is16HexCharsAndNonZero() {
        // Act
        String spanId = TraceIdentifierFactory.newSpanId();

        // Assert
        assertThat(spanId).hasSize(16).matches("[0-9a-f]+");
        assertThat(TraceIdentifierFactory.isValid(spanId, 16)).isTrue();
    }

    @Test
    void isValid_rejectsNull() {
        assertThat(TraceIdentifierFactory.isValid(null, 32)).isFalse();
    }

    @Test
    void isValid_rejectsWrongLength() {
        assertThat(TraceIdentifierFactory.isValid("abcd", 32)).isFalse();
    }

    @Test
    void isValid_rejectsNonHexCharacters() {
        // 32 chars but contains 'g' and uppercase
        assertThat(TraceIdentifierFactory.isValid("g000000000000000000000000000000z", 32)).isFalse();
    }

    @Test
    void isValid_rejectsAllZero() {
        assertThat(TraceIdentifierFactory.isValid("0".repeat(32), 32)).isFalse();
    }

    @Test
    void isValid_acceptsWellFormedValue() {
        assertThat(TraceIdentifierFactory.isValid("00000000000000000000000000000001", 32)).isTrue();
    }

    @Test
    void constructor_isNotInstantiable() throws NoSuchMethodException {
        // Arrange
        Constructor<TraceIdentifierFactory> constructor = TraceIdentifierFactory.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // Act
        Throwable thrown = catchThrowable(constructor::newInstance);

        // Assert
        assertThat(thrown).isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(AssertionError.class);
    }
}
