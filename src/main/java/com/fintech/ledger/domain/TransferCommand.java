package com.fintech.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record TransferCommand(
        String idempotencyKey,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency
) {
    public TransferCommand {
        Objects.requireNonNull(idempotencyKey, "Idempotency key is required");
        Objects.requireNonNull(sourceAccountId, "Source account ID is required");
        Objects.requireNonNull(destinationAccountId, "Destination account ID is required");
        Objects.requireNonNull(amount, "Amount is required");
        Objects.requireNonNull(currency, "Currency is required");

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive");
        }
    }
}