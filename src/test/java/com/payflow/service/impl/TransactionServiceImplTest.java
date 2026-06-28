package com.payflow.service.impl;

import com.payflow.concurrency.StripedLockRegistry;
import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.enums.Currency;
import com.payflow.enums.TransactionStatus;
import com.payflow.event.TransactionEvent;
import com.payflow.exception.InvalidTransferException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.TransactionMapper;
import com.payflow.messaging.TransactionEventPublisher;
import com.payflow.repository.TransactionRepository;
import com.payflow.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionServiceImpl} (AAA pattern). The striped lock registry is
 * stubbed to invoke the supplied action so the orchestration is exercised without real locking.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransferExecutor transferExecutor;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private StripedLockRegistry lockRegistry;
    @Mock
    private TransactionEventPublisher eventPublisher;
    @Mock
    private AuditService auditService;
    @Mock
    private CacheManager cacheManager;

    private TransactionServiceImpl service;

    private void initService() {
        service = new TransactionServiceImpl(transferExecutor, transactionRepository, transactionMapper,
                lockRegistry, eventPublisher, auditService, cacheManager);
    }

    @SuppressWarnings("unchecked")
    private void stubLockToRunAction() {
        when(lockRegistry.executeWithLocks(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(2)).get());
    }

    private Transaction completedTransaction() {
        return Transaction.builder()
                .referenceId("TXN_1")
                .senderUpiId("alice@okaxis")
                .receiverUpiId("bob@oksbi")
                .amount(new BigDecimal("250.00"))
                .currency(Currency.INR)
                .status(TransactionStatus.COMPLETED)
                .build();
    }

    private TransactionResponse sampleResponse() {
        return new TransactionResponse("TXN_1", "alice@okaxis", "bob@oksbi",
                new BigDecimal("250.00"), Currency.INR, TransactionStatus.COMPLETED, null, null, Instant.now());
    }

    @Test
    void sendMoney_executesPublishesAuditsAndEvictsCache() {
        // Arrange
        initService();
        stubLockToRunAction();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("usersByUpiId")).thenReturn(cache);
        Transaction txn = completedTransaction();
        when(transferExecutor.execute(any(SendMoneyRequest.class))).thenReturn(txn);
        when(transactionMapper.toResponse(txn)).thenReturn(sampleResponse());
        SendMoneyRequest request = new SendMoneyRequest("Alice@OkAxis", "Bob@OkSbi",
                new BigDecimal("250.00"), "lunch");

        // Act
        TransactionResponse result = service.sendMoney(request);

        // Assert
        assertThat(result.referenceId()).isEqualTo("TXN_1");
        ArgumentCaptor<TransactionEvent> eventCaptor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(auditService).record(eq("TRANSACTION_COMPLETED"), eq("alice@okaxis"), eq("Transaction"),
                eq("TXN_1"), anyString());
        verify(cache).evictIfPresent("alice@okaxis");
        verify(cache).evictIfPresent("bob@oksbi");
    }

    @Test
    void sendMoney_toleratesNullCache() {
        // Arrange
        initService();
        stubLockToRunAction();
        when(cacheManager.getCache("usersByUpiId")).thenReturn(null);
        Transaction txn = completedTransaction();
        when(transferExecutor.execute(any(SendMoneyRequest.class))).thenReturn(txn);
        when(transactionMapper.toResponse(txn)).thenReturn(sampleResponse());

        // Act
        TransactionResponse result = service.sendMoney(
                new SendMoneyRequest("alice@okaxis", "bob@oksbi", new BigDecimal("250.00"), null));

        // Assert
        assertThat(result).isNotNull();
        verify(eventPublisher).publish(any(TransactionEvent.class));
    }

    @Test
    void sendMoney_rejectsTransferToSelf() {
        // Arrange
        initService();
        SendMoneyRequest request = new SendMoneyRequest("alice@okaxis", "Alice@OkAxis",
                new BigDecimal("10.00"), null);

        // Act + Assert
        assertThatThrownBy(() -> service.sendMoney(request))
                .isInstanceOf(InvalidTransferException.class);
        verify(transferExecutor, never()).execute(any());
    }

    @Test
    void getByReference_returnsMappedWhenFound() {
        // Arrange
        initService();
        Transaction txn = completedTransaction();
        when(transactionRepository.findByReferenceId("TXN_1")).thenReturn(Optional.of(txn));
        when(transactionMapper.toResponse(txn)).thenReturn(sampleResponse());

        // Act
        TransactionResponse result = service.getByReference("TXN_1");

        // Assert
        assertThat(result.referenceId()).isEqualTo("TXN_1");
    }

    @Test
    void getByReference_throwsWhenMissing() {
        initService();
        when(transactionRepository.findByReferenceId("TXN_X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByReference("TXN_X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHistory_mapsPage() {
        // Arrange
        initService();
        Pageable pageable = PageRequest.of(0, 20);
        Transaction txn = completedTransaction();
        Page<Transaction> page = new PageImpl<>(List.of(txn), pageable, 1);
        when(transactionRepository.findHistoryForUpiId("alice@okaxis", pageable)).thenReturn(page);
        lenient().when(transactionMapper.toResponse(txn)).thenReturn(sampleResponse());

        // Act
        Page<TransactionResponse> result = service.getHistory("Alice@OkAxis", pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }
}
