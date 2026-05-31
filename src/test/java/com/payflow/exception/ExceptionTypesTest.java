package com.payflow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the exception hierarchy, factory methods, and {@link ErrorCode} catalogue.
 */
class ExceptionTypesTest {

    @Test
    void errorCode_exposesStatusAndMessageForEveryConstant() {
        // Act + Assert
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getStatus()).isNotNull();
            assertThat(code.getDefaultMessage()).isNotBlank();
        }
        assertThat(ErrorCode.valueOf("USER_NOT_FOUND").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resourceNotFound_userFactoryCarriesCode() {
        // Act
        ResourceNotFoundException ex = ResourceNotFoundException.user("priya@okaxis");

        // Assert
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).contains("priya@okaxis");
    }

    @Test
    void resourceNotFound_transactionFactoryCarriesCode() {
        ResourceNotFoundException ex = ResourceNotFoundException.transaction("TXN_1");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
        assertThat(ex.getMessage()).contains("TXN_1");
    }

    @Test
    void duplicateResource_upiIdFactoryCarriesCode() {
        DuplicateResourceException ex = DuplicateResourceException.upiId("priya@okaxis");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_UPI_ID);
        assertThat(ex.getMessage()).contains("priya@okaxis");
    }

    @Test
    void insufficientBalance_carriesUnprocessableCode() {
        InsufficientBalanceException ex = new InsufficientBalanceException("low");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(ex.getMessage()).isEqualTo("low");
    }

    @Test
    void invalidTransfer_carriesBadRequestCode() {
        InvalidTransferException ex = new InvalidTransferException("same party");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRANSFER);
    }

    @Test
    void rateLimitExceeded_carriesTooManyRequestsCode() {
        RateLimitExceededException ex = new RateLimitExceededException("slow down");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
