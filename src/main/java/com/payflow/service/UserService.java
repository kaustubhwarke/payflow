package com.payflow.service;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * User-management use cases (Interface Segregation + Dependency Inversion: controllers and
 * collaborators depend on this abstraction, not the implementation).
 */
public interface UserService {

    /**
     * Registers a new user. Fails if the UPI ID is already taken.
     *
     * @param request validated registration payload
     * @return the created user
     */
    UserResponse registerUser(CreateUserRequest request);

    /**
     * @param referenceId external user reference
     * @return the matching user
     */
    UserResponse getByReference(String referenceId);

    /**
     * @param upiId UPI ID to look up (Task 6 lookup)
     * @return the matching user
     */
    UserResponse getByUpiId(String upiId);

    /**
     * @param pageable pagination directives
     * @return a page of all users
     */
    Page<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Custom-query use case (Task 6): users whose balance exceeds a threshold.
     *
     * @param threshold minimum (exclusive) balance
     * @param pageable  pagination directives
     * @return a page of matching users
     */
    Page<UserResponse> getUsersWithBalanceAbove(BigDecimal threshold, Pageable pageable);
}
