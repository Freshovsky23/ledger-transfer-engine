package com.fintech.ledger.repository;

import com.fintech.ledger.domain.Account;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByIdForUpdate(Connection conn, String accountId);
    void updateBalance(Connection conn, String accountId, BigDecimal newBalance);
}