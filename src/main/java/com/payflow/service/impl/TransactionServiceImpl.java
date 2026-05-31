package com.payflow.service.impl;

import com.payflow.concurrency.StripedLockRegistry;
import com.payflow.config.CacheConfig;
import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.event.TransactionEvent;
import com.payflow.exception.InvalidTransferException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.TransactionMapper;
import com.payflow.messaging.TransactionEventPublisher;
import com.payflow.repository.TransactionRepository;
import com.payflow.service.AuditService;
import com.payflow.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Default {@link TransactionService} implementation.
 *
 * <p>Orchestrates a transfer in three phases: (1) cheap semantic validation, (2) the atomic
 * money movement performed by {@link TransferExecutor} under fine-grained per-account striped
 * locks, and (3) post-commit side-effects (event publication + audit) that must not run until
 * the ledger write is durable.</p>
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransferExecutor transferExecutor;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final StripedLockRegistry lockRegistry;
    private final TransactionEventPublisher eventPublisher;
    private final AuditService auditService;
    private final CacheManager cacheManager;

    public TransactionServiceImpl(TransferExecutor transferExecutor,
                                  TransactionRepository transactionRepository,
                                  TransactionMapper transactionMapper,
                                  StripedLockRegistry lockRegistry,
                                  TransactionEventPublisher eventPublisher,
                                  AuditService auditService,
                                  CacheManager cacheManager) {
        this.transferExecutor = transferExecutor;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.lockRegistry = lockRegistry;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.cacheManager = cacheManager;
    }

    @Override
    public TransactionResponse sendMoney(SendMoneyRequest request) {
        String senderUpi = request.senderUpiId().toLowerCase();
        String receiverUpi = request.receiverUpiId().toLowerCase();

        if (senderUpi.equals(receiverUpi)) {
            throw new InvalidTransferException("Sender and receiver UPI IDs must differ");
        }

        // Phase 2: serialise per-account; the transactional executor commits before locks release.
        Transaction transaction = lockRegistry.executeWithLocks(senderUpi, receiverUpi,
                () -> transferExecutor.execute(request));

        log.info("Completed transfer reference={} from={} to={} amount={}",
                transaction.getReferenceId(), senderUpi, receiverUpi, transaction.getAmount());

        // Both parties' balances changed: drop any cached lookups so reads never go stale.
        evictUserCache(senderUpi);
        evictUserCache(receiverUpi);

        // Phase 3: post-commit side-effects.
        publishCompleted(transaction);
        auditService.record("TRANSACTION_COMPLETED", senderUpi, "Transaction",
                transaction.getReferenceId(), "Transferred " + transaction.getAmount() + " to " + receiverUpi);

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getByReference(String referenceId) {
        return transactionRepository.findByReferenceId(referenceId)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.transaction(referenceId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getHistory(String upiId, Pageable pageable) {
        return transactionRepository.findHistoryForUpiId(upiId.toLowerCase(), pageable)
                .map(transactionMapper::toResponse);
    }

    private void evictUserCache(String upiId) {
        Cache cache = cacheManager.getCache(CacheConfig.USERS_BY_UPI_ID);
        if (cache != null) {
            cache.evictIfPresent(upiId);
        }
    }

    private void publishCompleted(Transaction transaction) {
        eventPublisher.publish(new TransactionEvent(
                transaction.getReferenceId(),
                transaction.getSenderUpiId(),
                transaction.getReceiverUpiId(),
                transaction.getAmount(),
                transaction.getStatus(),
                Instant.now(),
                MDC.get("traceId")));
    }
}
