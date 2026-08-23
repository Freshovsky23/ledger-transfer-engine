package com.fintech.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Account(
        String id,
        String userId,
        String currency,
        BigDecimal balance
) {
    public Account {
        Objects.requireNonNull(id, "Account ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        Objects.requireNonNull(balance, "Balance cannot be null");
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Account balance cannot be negative");
        }
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    public Account debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be strictly positive");
        }
        if (!hasSufficientFunds(amount)) {
            throw new IllegalStateException("Insufficient funds in account: " + id);
        }
        return new Account(id, userId, currency, balance.subtract(amount));
    }

    public Account credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }
        return new Account(id, userId, currency, balance.add(amount));
    }
}