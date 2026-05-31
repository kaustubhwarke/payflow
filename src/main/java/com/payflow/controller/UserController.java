package com.payflow.controller;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

/**
 * REST API for user management (Rule 2). Versioned under {@code /api/v1} to support a clean
 * evolution strategy (mentor feedback 22.b). All endpoints require a valid Keycloak JWT;
 * privileged operations additionally require the {@code ADMIN} realm role.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Register and look up PayFlow users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Register a new user",
            description = "Creates a wallet-owning user. The UPI ID must be unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "409", description = "UPI ID already registered", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody CreateUserRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        UserResponse created = userService.registerUser(request);
        URI location = uriBuilder.path("/api/v1/users/{referenceId}")
                .buildAndExpand(created.referenceId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List all users (paginated)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<UserResponse> getAllUsers(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(userService.getAllUsers(pageable));
    }

    @Operation(summary = "Get a user by external reference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
    })
    @GetMapping("/{referenceId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse getByReference(
            @Parameter(description = "External user reference, e.g. USR_01HZ...") @PathVariable String referenceId) {
        return userService.getByReference(referenceId);
    }

    @Operation(summary = "Look up a user by UPI ID",
            description = "Backs the pre-transfer payee lookup (assignment Task 6).")
    @GetMapping("/upi/{upiId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse getByUpiId(
            @Parameter(description = "UPI ID, e.g. priya@okaxis") @PathVariable String upiId) {
        return userService.getByUpiId(upiId);
    }

    @Operation(summary = "Find users whose balance exceeds a threshold (paginated)",
            description = "Demonstrates the custom JPQL @Query (assignment Task 6).")
    @GetMapping("/search/by-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<UserResponse> getByBalanceAbove(
            @Parameter(description = "Exclusive minimum balance", example = "1000.00")
            @RequestParam @Schema(type = "number") BigDecimal threshold,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(userService.getUsersWithBalanceAbove(threshold, pageable));
    }
}
