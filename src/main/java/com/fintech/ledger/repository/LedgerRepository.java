package com.fintech.ledger.repository;

import com.fintech.ledger.domain.TransferCommand;
import java.sql.Connection;

public interface LedgerRepository {
    void createTransfer(Connection conn, String transferId, TransferCommand command);
    void recordDoubleEntry(Connection conn, String transferId, TransferCommand command);
}