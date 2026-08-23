package com.fintech.ledger.repository;

import com.fintech.ledger.domain.TransferCommand;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class PostgresLedgerRepository implements LedgerRepository {

    private static final String INSERT_TRANSFER_SQL =
            "INSERT INTO transfers (id, idempotency_key, source_account_id, destination_account_id, amount, currency, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED'::transfer_status)";

    private static final String INSERT_LEDGER_ENTRY_SQL =
            "INSERT INTO ledger_entries (id, transfer_id, account_id, amount, direction) " +
                    "VALUES (?, ?, ?, ?, ?::entry_direction)";

    @Override
    public void createTransfer(Connection conn, String transferId, TransferCommand command) {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRANSFER_SQL)) {
            stmt.setString(1, transferId);
            stmt.setString(2, command.idempotencyKey());
            stmt.setString(3, command.sourceAccountId());
            stmt.setString(4, command.destinationAccountId());
            stmt.setBigDecimal(5, command.amount());
            stmt.setString(6, command.currency());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting transfer record: " + transferId, e);
        }
    }

    @Override
    public void recordDoubleEntry(Connection conn, String transferId, TransferCommand command) {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_LEDGER_ENTRY_SQL)) {
            // 1. Zapis DEBETOWY (obciążenie konta źródłowego)
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, transferId);
            stmt.setString(3, command.sourceAccountId());
            stmt.setBigDecimal(4, command.amount());
            stmt.setString(5, "DEBIT");
            stmt.addBatch();

            // 2. Zapis KREDYTOWY (uznanie konta docelowego)
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, transferId);
            stmt.setString(3, command.destinationAccountId());
            stmt.setBigDecimal(4, command.amount());
            stmt.setString(5, "CREDIT");
            stmt.addBatch();

            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating double-entry ledger records for transfer: " + transferId, e);
        }
    }
}