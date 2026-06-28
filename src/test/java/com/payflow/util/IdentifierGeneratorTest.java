package com.payflow.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for {@link IdentifierGenerator} (AAA pattern).
 */
class IdentifierGeneratorTest {

    @Test
    void newUlid_returns26CrockfordChars() {
        // Act
        String ulid = IdentifierGenerator.newUlid();

        // Assert
        assertThat(ulid).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]+");
    }

    @Test
    void newUlid_isUniqueAcrossInvocations() {
        // Act
        String first = IdentifierGenerator.newUlid();
        String second = IdentifierGenerator.newUlid();

        // Assert
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void newReference_prependsPrefixWithSeparator() {
        // Act
        String reference = IdentifierGenerator.newReference("TXN");

        // Assert
        assertThat(reference).startsWith("TXN_");
        assertThat(reference.substring(4)).hasSize(26);
    }

    @Test
    void newReference_rejectsBlankPrefix() {
        // Act + Assert
        assertThatThrownBy(() -> IdentifierGenerator.newReference("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentifierGenerator.newReference(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_isNotInstantiable() throws NoSuchMethodException {
        // Arrange
        Constructor<IdentifierGenerator> constructor = IdentifierGenerator.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // Act
        Throwable thrown = catchThrowable(constructor::newInstance);

        // Assert
        assertThat(thrown).isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(AssertionError.class);
    }
}
