package com.payflow.service.impl;

import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.entity.Transaction;
import com.payflow.entity.User;
import com.payflow.enums.Currency;
import com.payflow.enums.TransactionStatus;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransferExecutor} (AAA pattern) — the atomic debit/credit core.
 */
@ExtendWith(MockitoExtension.class)
class TransferExecutorTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private TransferExecutor transferExecutor;

    private User user(String upiId, String balance) {
        return User.builder().upiId(upiId).balance(new BigDecimal(balance)).currency(Currency.INR).build();
    }

    private SendMoneyRequest request(String amount) {
        return new SendMoneyRequest("alice@okaxis", "bob@oksbi", new BigDecimal(amount), "lunch");
    }

    @Test
    void execute_debitsSenderCreditsReceiverAndRecordsCompleted() {
        // Arrange
        User sender = user("alice@okaxis", "1000.00");
        User receiver = user("bob@oksbi", "200.00");
        when(userRepository.findByUpiId("alice@okaxis")).thenReturn(Optional.of(sender));
        when(userRepository.findByUpiId("bob@oksbi")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Transaction result = transferExecutor.execute(request("250.00"));

        // Assert
        assertThat(sender.getBalance()).isEqualByComparingTo("750.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("450.00");
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getReferenceId()).startsWith("TXN_");
        assertThat(saved.getNote()).isEqualTo("lunch");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void execute_throwsWhenSenderMissing() {
        when(userRepository.findByUpiId("alice@okaxis")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferExecutor.execute(request("250.00")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_throwsWhenReceiverMissing() {
        when(userRepository.findByUpiId("alice@okaxis")).thenReturn(Optional.of(user("alice@okaxis", "1000")));
        when(userRepository.findByUpiId("bob@oksbi")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferExecutor.execute(request("250.00")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_throwsWhenSenderHasInsufficientBalance() {
        when(userRepository.findByUpiId("alice@okaxis")).thenReturn(Optional.of(user("alice@okaxis", "100.00")));
        when(userRepository.findByUpiId("bob@oksbi")).thenReturn(Optional.of(user("bob@oksbi", "0.00")));

        assertThatThrownBy(() -> transferExecutor.execute(request("250.00")))
                .isInstanceOf(InsufficientBalanceException.class);
        verify(transactionRepository, never()).save(any());
    }
}
