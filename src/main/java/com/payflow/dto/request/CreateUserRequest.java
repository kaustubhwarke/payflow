package com.payflow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload to register a new user (POST /api/v1/users).
 *
 * <p>Implemented as an immutable record with comprehensive bean-validation constraints so the
 * API contract rejects malformed input before it reaches the service layer
 * (mentor feedback 22.b).</p>
 *
 * @param name           full name of the user
 * @param upiId          unique UPI handle, e.g. {@code priya@okaxis}
 * @param phoneNumber    10-digit Indian mobile number
 * @param openingBalance opening wallet balance (>= 0), max two decimal places
 */
@Schema(name = "CreateUserRequest", description = "Payload to register a new PayFlow user")
public record CreateUserRequest(

        @Schema(example = "Priya Sharma", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must not exceed 120 characters")
        String name,

        @Schema(example = "priya@okaxis", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "upiId is required")
        @Size(max = 80, message = "upiId must not exceed 80 characters")
        @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$",
                message = "upiId must be in the form handle@provider")
        String upiId,

        @Schema(example = "9876543210", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "phoneNumber is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "phoneNumber must be a valid 10-digit Indian mobile number")
        String phoneNumber,

        @Schema(example = "5000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "openingBalance is required")
        @DecimalMin(value = "0.00", message = "openingBalance must not be negative")
        @Digits(integer = 17, fraction = 2, message = "openingBalance must have at most 2 decimal places")
        BigDecimal openingBalance
) {
}
