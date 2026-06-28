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
import com.payflow.util.IdentifierGenerator;
import com.payflow.util.InputSanitizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Executes the atomic debit/credit + ledger write inside a single database transaction.
 *
 * <p>This is intentionally a separate bean from {@code TransactionServiceImpl}: the service
 * acquires the fine-grained account locks <em>around</em> a call to this proxied, transactional
 * method, guaranteeing the database commit happens <b>before</b> the application locks are
 * released (mentor feedback 22.a). The {@code @Version} columns provide a second, database-level
 * line of defence against lost updates.</p>
 */
@Component
public class TransferExecutor {

    private static final String TXN_REFERENCE_PREFIX = "TXN";

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransferExecutor(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Debits the sender, credits the receiver, and records a COMPLETED transaction.
     *
     * @param request the (already structurally validated) transfer request
     * @return the persisted, completed transaction
     * @throws ResourceNotFoundException   if either party does not exist
     * @throws InsufficientBalanceException if the sender cannot cover the amount
     */
    @Transactional
    public Transaction execute(SendMoneyRequest request) {
        String senderUpi = request.senderUpiId().toLowerCase();
        String receiverUpi = request.receiverUpiId().toLowerCase();
        BigDecimal amount = request.amount();

        User sender = userRepository.findByUpiId(senderUpi)
                .orElseThrow(() -> ResourceNotFoundException.user(senderUpi));
        User receiver = userRepository.findByUpiId(receiverUpi)
                .orElseThrow(() -> ResourceNotFoundException.user(receiverUpi));

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Sender '" + senderUpi + "' has insufficient balance for amount " + amount);
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        Transaction transaction = Transaction.builder()
                .referenceId(IdentifierGenerator.newReference(TXN_REFERENCE_PREFIX))
                .senderUpiId(senderUpi)
                .receiverUpiId(receiverUpi)
                .amount(amount)
                .currency(Currency.INR)
                .status(TransactionStatus.COMPLETED)
                .note(InputSanitizer.sanitize(request.note()))
                .build();

        return transactionRepository.save(transaction);
    }
}
