package com.fintech.ledger.service;

import com.fintech.ledger.domain.Account;
import com.fintech.ledger.domain.TransferCommand;
import com.fintech.ledger.domain.TransferResult;
import com.fintech.ledger.repository.AccountRepository;
import com.fintech.ledger.repository.LedgerRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public class TransferService {

    private final DataSource dataSource;
    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyService idempotencyService;

    public TransferService(DataSource dataSource,
                           AccountRepository accountRepository,
                           LedgerRepository ledgerRepository,
                           IdempotencyService idempotencyService) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository);
        this.idempotencyService = Objects.requireNonNull(idempotencyService);
    }

    public TransferResult executeTransfer(TransferCommand command) {
        // 1. Sprawdzenie klucza w Redis
        if (idempotencyService.isProcessed(command.idempotencyKey())) {
            return idempotencyService.getCachedResult(command.idempotencyKey())
                    .orElseGet(() -> TransferResult.cached("UNKNOWN"));
        }

        String transferId = UUID.randomUUID().toString();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Początek transakcji ACID

            try {
                // 2. Zapobieganie zakleszczeniom (Deadlock Prevention)
                String firstLock = command.sourceAccountId().compareTo(command.destinationAccountId()) < 0
                        ? command.sourceAccountId() : command.destinationAccountId();
                String secondLock = firstLock.equals(command.sourceAccountId())
                        ? command.destinationAccountId() : command.sourceAccountId();

                Account firstAcc = lockAccount(conn, firstLock);
                Account secondAcc = lockAccount(conn, secondLock);

                Account source = firstAcc.id().equals(command.sourceAccountId()) ? firstAcc : secondAcc;
                Account dest = secondAcc.id().equals(command.destinationAccountId()) ? secondAcc : firstAcc;

                // 3. Walidacja waluty
                if (!source.currency().equalsIgnoreCase(command.currency()) ||
                        !dest.currency().equalsIgnoreCase(command.currency())) {
                    throw new IllegalArgumentException("Currency mismatch between accounts and command");
                }

                // 4. Modyfikacja sald
                Account debitedSource = source.debit(command.amount());
                Account creditedDest = dest.credit(command.amount());

                // 5. Zapis w bazie
                accountRepository.updateBalance(conn, debitedSource.id(), debitedSource.balance());
                accountRepository.updateBalance(conn, creditedDest.id(), creditedDest.balance());
                ledgerRepository.createTransfer(conn, transferId, command);
                ledgerRepository.recordDoubleEntry(conn, transferId, command);

                conn.commit(); // Zatwierdzenie transakcji
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Transaction failed", e);
        }

        TransferResult result = TransferResult.success(transferId);
        idempotencyService.markProcessed(command.idempotencyKey(), result);
        return result;
    }

    private Account lockAccount(Connection conn, String accountId) {
        return accountRepository.findByIdForUpdate(conn, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }
}