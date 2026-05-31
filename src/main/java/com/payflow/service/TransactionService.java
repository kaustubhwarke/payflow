package com.payflow.service;

import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Money-movement use cases.
 */
public interface TransactionService {

    /**
     * Records a money transfer between two parties, debiting the sender and crediting the
     * receiver atomically under fine-grained per-account locking.
     *
     * @param request validated transfer payload
     * @return the persisted transaction
     */
    TransactionResponse sendMoney(SendMoneyRequest request);

    /**
     * @param referenceId external transaction reference
     * @return the matching transaction
     */
    TransactionResponse getByReference(String referenceId);

    /**
     * Paginated transaction history for a party (Rule 16).
     *
     * @param upiId    the party's UPI ID
     * @param pageable pagination directives
     * @return a page of transactions involving the party
     */
    Page<TransactionResponse> getHistory(String upiId, Pageable pageable);
}
