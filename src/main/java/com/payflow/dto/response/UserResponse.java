package com.payflow.dto.response;

import com.payflow.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of a {@link com.payflow.entity.User}. Deliberately omits the internal
 * surrogate primary key and version, exposing only the opaque {@code referenceId}.
 */
@Schema(name = "UserResponse", description = "A registered PayFlow user")
public record UserResponse(

        @Schema(example = "USR_01HZX3K8N2Q7R5", description = "Opaque external user reference")
        String referenceId,

        @Schema(example = "Priya Sharma")
        String name,

        @Schema(example = "priya@okaxis")
        String upiId,

        @Schema(example = "9876543210")
        String phoneNumber,

        @Schema(example = "5000.00")
        BigDecimal balance,

        @Schema(example = "INR")
        Currency currency,

        @Schema(example = "2026-05-31T10:15:30Z")
        Instant createdAt
) {
}
