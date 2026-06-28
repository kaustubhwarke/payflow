package com.payflow.controller;

import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import java.net.URI;

/**
 * REST API for money movement (Rule 2). Transaction history is paginated (Rule 16).
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Record money transfers and read transaction history")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Send money",
            description = "Records a transfer, debiting the sender and crediting the receiver atomically.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid transfer (e.g. sender == receiver)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sender or receiver not found", content = @Content),
            @ApiResponse(responseCode = "422", description = "Insufficient balance", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> sendMoney(@Valid @RequestBody SendMoneyRequest request,
                                                         UriComponentsBuilder uriBuilder) {
        TransactionResponse created = transactionService.sendMoney(request);
        URI location = uriBuilder.path("/api/v1/transactions/{referenceId}")
                .buildAndExpand(created.referenceId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get a transaction by external reference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
    })
    @GetMapping("/{referenceId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public TransactionResponse getByReference(
            @Parameter(description = "External transaction reference, e.g. TXN_01HZ...")
            @PathVariable String referenceId) {
        return transactionService.getByReference(referenceId);
    }

    @Operation(summary = "Transaction history for a UPI ID (paginated)",
            description = "Returns transfers where the UPI ID is either sender or receiver, newest first.")
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PagedResponse<TransactionResponse> getHistory(
            @Parameter(description = "Party UPI ID", example = "priya@okaxis") @RequestParam String upiId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PagedResponse.from(transactionService.getHistory(upiId, pageable));
    }
}
