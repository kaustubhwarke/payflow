package com.payflow.controller;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.enums.Currency;
import com.payflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserController} (AAA pattern) — invoked directly with a mocked service.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @InjectMocks
    private UserController userController;

    private UserResponse response() {
        return new UserResponse("USR_1", "Priya", "priya@okaxis", "9876543210",
                new BigDecimal("5000.00"), Currency.INR, Instant.now());
    }

    @Test
    void registerUser_returns201WithLocationHeader() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("Priya", "priya@okaxis",
                "9876543210", new BigDecimal("5000.00"));
        when(userService.registerUser(request)).thenReturn(response());

        // Act
        ResponseEntity<UserResponse> result = userController.registerUser(request,
                UriComponentsBuilder.fromUriString("http://localhost"));

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath()).isEqualTo("/api/v1/users/USR_1");
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().referenceId()).isEqualTo("USR_1");
    }

    @Test
    void getAllUsers_wrapsPageInPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserResponse> page = new PageImpl<>(List.of(response()), pageable, 1);
        when(userService.getAllUsers(pageable)).thenReturn(page);

        // Act
        PagedResponse<UserResponse> result = userController.getAllUsers(pageable);

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.last()).isTrue();
    }

    @Test
    void getByReference_delegatesToService() {
        when(userService.getByReference("USR_1")).thenReturn(response());
        assertThat(userController.getByReference("USR_1").referenceId()).isEqualTo("USR_1");
    }

    @Test
    void getByUpiId_delegatesToService() {
        when(userService.getByUpiId("priya@okaxis")).thenReturn(response());
        assertThat(userController.getByUpiId("priya@okaxis").upiId()).isEqualTo("priya@okaxis");
    }

    @Test
    void getByBalanceAbove_wrapsPageInPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        BigDecimal threshold = new BigDecimal("1000.00");
        Page<UserResponse> page = new PageImpl<>(List.of(response()), pageable, 1);
        when(userService.getUsersWithBalanceAbove(threshold, pageable)).thenReturn(page);

        // Act
        PagedResponse<UserResponse> result = userController.getByBalanceAbove(threshold, pageable);

        // Assert
        assertThat(result.content()).hasSize(1);
    }
}
