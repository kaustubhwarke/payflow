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
 * Request payload to record a money transfer (POST /api/v1/transactions).
 *
 * @param senderUpiId   UPI ID of the paying party
 * @param receiverUpiId UPI ID of the receiving party
 * @param amount        transfer amount (> 0), max two decimal places
 * @param note          optional free-text note
 */
@Schema(name = "SendMoneyRequest", description = "Payload to initiate a money transfer")
public record SendMoneyRequest(

        @Schema(example = "priya@okaxis", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "senderUpiId is required")
        @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$",
                message = "senderUpiId must be in the form handle@provider")
        String senderUpiId,

        @Schema(example = "rohan@oksbi", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "receiverUpiId is required")
        @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$",
                message = "receiverUpiId must be in the form handle@provider")
        String receiverUpiId,

        @Schema(example = "250.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "dinner split")
        @Size(max = 255, message = "note must not exceed 255 characters")
        String note
) {
}
