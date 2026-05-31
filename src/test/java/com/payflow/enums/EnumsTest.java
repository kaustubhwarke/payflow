package com.payflow.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the domain enums' generated {@code values()}/{@code valueOf(..)} members.
 */
class EnumsTest {

    @Test
    void role_valuesAndValueOf() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }

    @Test
    void transactionStatus_valuesAndValueOf() {
        assertThat(TransactionStatus.values())
                .containsExactly(TransactionStatus.PENDING, TransactionStatus.COMPLETED,
                        TransactionStatus.FAILED, TransactionStatus.REVERSED);
        assertThat(TransactionStatus.valueOf("COMPLETED")).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void currency_valuesAndValueOf() {
        assertThat(Currency.values()).containsExactly(Currency.INR);
        assertThat(Currency.valueOf("INR")).isEqualTo(Currency.INR);
    }
}
