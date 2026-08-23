package com.fintech.ledger.api.dto;

import java.math.BigDecimal;

public record CreateTransferRequest(
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency
) {}