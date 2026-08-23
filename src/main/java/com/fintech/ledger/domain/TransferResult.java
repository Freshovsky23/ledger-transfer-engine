package com.fintech.ledger.domain;

public record TransferResult(
        Status status,
        String message,
        String transferId
) {
    public enum Status {
        COMPLETED,
        FAILED,
        REJECTED
    }

    public static TransferResult success(String transferId) {
        return new TransferResult(Status.COMPLETED, "Transfer executed successfully", transferId);
    }

    public static TransferResult cached(String transferId) {
        return new TransferResult(Status.COMPLETED, "Transfer already processed (cached)", transferId);
    }

    public static TransferResult failed(String reason) {
        return new TransferResult(Status.REJECTED, reason, null);
    }
}