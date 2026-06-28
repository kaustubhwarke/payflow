package com.payflow.repository;

import com.payflow.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence gateway for {@link Transaction} records.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** @return the transaction with the given external reference, if any. */
    Optional<Transaction> findByReferenceId(String referenceId);

    /**
     * Paginated transaction history for a UPI ID, matching where the party is either the
     * sender or the receiver (Rule 16: pagination via Spring Data {@link Pageable}).
     *
     * @param upiId    the party's UPI ID
     * @param pageable pagination/sort directives
     * @return a page of transactions involving the party
     */
    @Query("SELECT t FROM Transaction t "
            + "WHERE t.senderUpiId = :upiId OR t.receiverUpiId = :upiId")
    Page<Transaction> findHistoryForUpiId(@Param("upiId") String upiId, Pageable pageable);
}
