package com.payflow.repository;

import com.payflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Persistence gateway for {@link User} aggregates.
 *
 * <p>Extends {@link JpaRepository}; Spring Data supplies the implementation at runtime,
 * keeping the data-access concern fully separated from business logic (SoC, DIP).</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Derived query (Task 3). Spring Data parses the method name into:
     * {@code SELECT u FROM User u WHERE u.upiId = ?1}. The {@code ?1} placeholder is a bound
     * parameter for the supplied {@code upiId}, preventing SQL injection.
     *
     * @param upiId the UPI ID to look up
     * @return the matching user, if any
     */
    Optional<User> findByUpiId(String upiId);

    /** @return the user with the given external reference, if any. */
    Optional<User> findByReferenceId(String referenceId);

    /** @return {@code true} if a user already owns the given UPI ID (uniqueness pre-check). */
    boolean existsByUpiId(String upiId);

    /**
     * Custom JPQL query (Task 6): paginated list of users whose balance exceeds a threshold.
     * JPQL (not native SQL) keeps the query portable across databases and validated against
     * the entity model at startup.
     *
     * @param threshold minimum (exclusive) balance
     * @param pageable  pagination/sort directives
     * @return a page of matching users
     */
    @Query("SELECT u FROM User u WHERE u.balance > :threshold")
    Page<User> findUsersWithBalanceAbove(@Param("threshold") BigDecimal threshold, Pageable pageable);
}
