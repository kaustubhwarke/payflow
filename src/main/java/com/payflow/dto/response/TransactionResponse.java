package com.payflow.dto.response;

import com.payflow.enums.Currency;
import com.payflow.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of a {@link com.payflow.entity.Transaction}.
 */
@Schema(name = "TransactionResponse", description = "A recorded money transfer")
public record TransactionResponse(

        @Schema(example = "TXN_01HZX3K8N2Q7R5", description = "Opaque external transaction reference")
        String referenceId,

        @Schema(example = "priya@okaxis")
        String senderUpiId,

        @Schema(example = "rohan@oksbi")
        String receiverUpiId,

        @Schema(example = "250.00")
        BigDecimal amount,

        @Schema(example = "INR")
        Currency currency,

        @Schema(example = "COMPLETED")
        TransactionStatus status,

        @Schema(example = "dinner split")
        String note,

        @Schema(description = "Populated only when status is FAILED")
        String failureReason,

        @Schema(example = "2026-05-31T10:15:30Z")
        Instant createdAt
) {
}
