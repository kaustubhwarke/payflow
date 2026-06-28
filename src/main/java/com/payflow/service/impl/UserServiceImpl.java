package com.payflow.service.impl;

import com.payflow.config.CacheConfig;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.enums.Currency;
import com.payflow.exception.DuplicateResourceException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.UserMapper;
import com.payflow.repository.UserRepository;
import com.payflow.service.AuditService;
import com.payflow.service.UserService;
import com.payflow.util.IdentifierGenerator;
import com.payflow.util.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Default {@link UserService} implementation.
 *
 * <p>Single Responsibility: owns user lifecycle rules only. Collaborators (repository, mapper,
 * audit) are injected via the constructor &mdash; Spring resolves and supplies these beans at
 * startup (constructor injection, no field {@code @Autowired}), which is what makes the
 * dependency-inversion wiring work.</p>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String USER_REFERENCE_PREFIX = "USR";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, AuditService auditService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public UserResponse registerUser(CreateUserRequest request) {
        String upiId = request.upiId().toLowerCase();
        if (userRepository.existsByUpiId(upiId)) {
            throw DuplicateResourceException.upiId(upiId);
        }

        User user = User.builder()
                .referenceId(IdentifierGenerator.newReference(USER_REFERENCE_PREFIX))
                .name(InputSanitizer.sanitize(request.name()))
                .upiId(upiId)
                .phoneNumber(request.phoneNumber())
                .balance(request.openingBalance())
                .currency(Currency.INR)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered user reference={} upiId={}", saved.getReferenceId(), saved.getUpiId());
        auditService.record("USER_REGISTERED", saved.getUpiId(), "User", saved.getReferenceId(),
                "Opening balance " + saved.getBalance());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByReference(String referenceId) {
        return userRepository.findByReferenceId(referenceId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.user(referenceId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.USERS_BY_UPI_ID, key = "#upiId.toLowerCase()")
    public UserResponse getByUpiId(String upiId) {
        return userRepository.findByUpiId(upiId.toLowerCase())
                .map(userMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.user(upiId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersWithBalanceAbove(BigDecimal threshold, Pageable pageable) {
        return userRepository.findUsersWithBalanceAbove(threshold, pageable).map(userMapper::toResponse);
    }
}
