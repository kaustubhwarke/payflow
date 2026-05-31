package com.payflow.controller;

import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.enums.Currency;
import com.payflow.enums.TransactionStatus;
import com.payflow.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionController} (AAA pattern).
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;
    @InjectMocks
    private TransactionController transactionController;

    private TransactionResponse response() {
        return new TransactionResponse("TXN_1", "alice@okaxis", "bob@oksbi",
                new BigDecimal("250.00"), Currency.INR, TransactionStatus.COMPLETED, "lunch", null, Instant.now());
    }

    @Test
    void sendMoney_returns201WithLocationHeader() {
        // Arrange
        SendMoneyRequest request = new SendMoneyRequest("alice@okaxis", "bob@oksbi",
                new BigDecimal("250.00"), "lunch");
        when(transactionService.sendMoney(request)).thenReturn(response());

        // Act
        ResponseEntity<TransactionResponse> result = transactionController.sendMoney(request,
                UriComponentsBuilder.fromUriString("http://localhost"));

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath()).isEqualTo("/api/v1/transactions/TXN_1");
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().referenceId()).isEqualTo("TXN_1");
    }

    @Test
    void getByReference_delegatesToService() {
        when(transactionService.getByReference("TXN_1")).thenReturn(response());
        assertThat(transactionController.getByReference("TXN_1").referenceId()).isEqualTo("TXN_1");
    }

    @Test
    void getHistory_wrapsPageInPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<TransactionResponse> page = new PageImpl<>(List.of(response()), pageable, 1);
        when(transactionService.getHistory("alice@okaxis", pageable)).thenReturn(page);

        // Act
        PagedResponse<TransactionResponse> result = transactionController.getHistory("alice@okaxis", pageable);

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
