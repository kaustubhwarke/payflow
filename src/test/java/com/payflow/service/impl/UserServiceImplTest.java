package com.payflow.service.impl;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.enums.Currency;
import com.payflow.exception.DuplicateResourceException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.UserMapper;
import com.payflow.repository.UserRepository;
import com.payflow.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl} (AAA pattern). Collaborators are mocked so only the
 * service's own logic is under test.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private UserServiceImpl userService;

    private UserResponse sampleResponse(String upiId) {
        return new UserResponse("USR_1", "Priya", upiId, "9876543210",
                new BigDecimal("5000.00"), Currency.INR, Instant.now());
    }

    @Test
    void registerUser_persistsAndAuditsWhenUpiIdIsFree() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Priya", "Priya@OkAxis",
                "9876543210", new BigDecimal("5000.00"));
        when(userRepository.existsByUpiId("priya@okaxis")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse("priya@okaxis"));

        // Act
        UserResponse result = userService.registerUser(request);

        // Assert
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUpiId()).isEqualTo("priya@okaxis");          // normalised to lower-case
        assertThat(saved.getName()).isEqualTo("Priya");                  // sanitised
        assertThat(saved.getReferenceId()).startsWith("USR_");
        assertThat(saved.getCurrency()).isEqualTo(Currency.INR);
        verify(auditService).record(eq("USER_REGISTERED"), eq("priya@okaxis"), eq("User"), anyString(), anyString());
        assertThat(result.upiId()).isEqualTo("priya@okaxis");
    }

    @Test
    void registerUser_throwsWhenUpiIdAlreadyExists() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Priya", "priya@okaxis",
                "9876543210", new BigDecimal("5000.00"));
        when(userRepository.existsByUpiId("priya@okaxis")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getByReference_returnsMappedUserWhenFound() {
        // Arrange
        when(userRepository.findByReferenceId("USR_1")).thenReturn(Optional.of(new User()));
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse("priya@okaxis"));

        // Act
        UserResponse result = userService.getByReference("USR_1");

        // Assert
        assertThat(result.referenceId()).isEqualTo("USR_1");
    }

    @Test
    void getByReference_throwsWhenMissing() {
        when(userRepository.findByReferenceId("USR_X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getByReference("USR_X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByUpiId_returnsMappedUserWhenFound() {
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(Optional.of(new User()));
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse("priya@okaxis"));

        UserResponse result = userService.getByUpiId("Priya@OkAxis");

        assertThat(result.upiId()).isEqualTo("priya@okaxis");
    }

    @Test
    void getByUpiId_throwsWhenMissing() {
        when(userRepository.findByUpiId("ghost@okaxis")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getByUpiId("ghost@okaxis"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllUsers_mapsPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(new User()), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse("priya@okaxis"));

        // Act
        Page<UserResponse> result = userService.getAllUsers(pageable);

        // Assert
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getUsersWithBalanceAbove_mapsPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        BigDecimal threshold = new BigDecimal("1000");
        Page<User> page = new PageImpl<>(List.of(new User()), pageable, 1);
        when(userRepository.findUsersWithBalanceAbove(threshold, pageable)).thenReturn(page);
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse("priya@okaxis"));

        // Act
        Page<UserResponse> result = userService.getUsersWithBalanceAbove(threshold, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }
}
