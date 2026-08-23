package com.fintech.ledger.api.dto;

public record TransferResponse(
        String status,
        String message,
        String transferId
) {}