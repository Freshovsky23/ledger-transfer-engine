package com.fintech.ledger.service;

import com.fintech.ledger.domain.TransferResult;
import java.util.Optional;

public interface IdempotencyService {
    boolean isProcessed(String idempotencyKey);
    Optional<TransferResult> getCachedResult(String idempotencyKey);
    void markProcessed(String idempotencyKey, TransferResult result);
}