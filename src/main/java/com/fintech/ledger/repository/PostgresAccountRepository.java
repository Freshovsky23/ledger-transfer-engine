package com.fintech.ledger.repository;

import com.fintech.ledger.domain.Account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PostgresAccountRepository implements AccountRepository {

    private static final String SELECT_FOR_UPDATE_SQL =
            "SELECT id, user_id, currency, balance FROM accounts WHERE id = ? FOR UPDATE";

    private static final String UPDATE_BALANCE_SQL =
            "UPDATE accounts SET balance = ? WHERE id = ?";

    @Override
    public Optional<Account> findByIdForUpdate(Connection conn, String accountId) {
        try (PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_UPDATE_SQL)) {
            stmt.setString(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Account(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("currency"),
                            rs.getBigDecimal("balance")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error locking account: " + accountId, e);
        }
        return Optional.empty();
    }

    @Override
    public void updateBalance(Connection conn, String accountId, BigDecimal newBalance) {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_BALANCE_SQL)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setString(2, accountId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new IllegalStateException("Failed to update balance, account not found: " + accountId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error updating balance for account: " + accountId, e);
        }
    }
}