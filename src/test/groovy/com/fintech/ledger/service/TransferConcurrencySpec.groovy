package com.fintech.ledger.service

import com.fintech.ledger.config.DatabaseConfig
import com.fintech.ledger.domain.TransferCommand
import com.fintech.ledger.domain.TransferResult
import com.fintech.ledger.repository.PostgresAccountRepository
import com.fintech.ledger.repository.PostgresLedgerRepository
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future

class TransferConcurrencySpec extends Specification {

    @Shared
    DatabaseConfig dbConfig

    @Shared
    TransferService transferService

    def setupSpec() {
        dbConfig = new DatabaseConfig()
        def accountRepo = new PostgresAccountRepository()
        def ledgerRepo = new PostgresLedgerRepository()
        def idempotencyService = new RedisIdempotencyService(dbConfig.getJedisPool(), 3600)

        transferService = new TransferService(
                dbConfig.getDataSource(),
                accountRepo,
                ledgerRepo,
                idempotencyService
        )
    }

    def setup() {
        // Przygotowanie dwóch kont testowych z saldem początkowym 5000.00 PLN każde
        executeSql("""
            INSERT INTO accounts (id, user_id, currency, balance) VALUES
            ('conc-acc-A', 'user-A', 'PLN', 5000.0000),
            ('conc-acc-B', 'user-B', 'PLN', 5000.0000)
            ON CONFLICT (id) DO UPDATE SET balance = 5000.0000;
        """)
    }

    def cleanup() {
        // Czyszczenie danych po teście
        executeSql("""
            DELETE FROM ledger_entries WHERE account_id IN ('conc-acc-A', 'conc-acc-B');
            DELETE FROM transfers WHERE source_account_id IN ('conc-acc-A', 'conc-acc-B') OR destination_account_id IN ('conc-acc-A', 'conc-acc-B');
            DELETE FROM accounts WHERE id IN ('conc-acc-A', 'conc-acc-B');
        """)
    }

    def "should execute 100 concurrent bidirectional transfers without deadlocks or balance corruption"() {
        given: "100 concurrent transfer tasks (50 from A->B and 50 from B->A)"
        int threadCount = 20
        int totalTransfers = 100
        def executor = Executors.newFixedThreadPool(threadCount)
        def startLatch = new CountDownLatch(1)
        List<Callable<TransferResult>> tasks = []

        totalTransfers.times { i ->
            boolean aToB = (i % 2 == 0)
            String source = aToB ? "conc-acc-A" : "conc-acc-B"
            String dest = aToB ? "conc-acc-B" : "conc-acc-A"
            String key = "conc-idem-key-" + UUID.randomUUID().toString()
            def command = new TransferCommand(key, source, dest, new BigDecimal("10.00"), "PLN")

            tasks << ({
                startLatch.await() // Wstrzymanie wątków do momentu jednoczesnego startu
                return transferService.executeTransfer(command)
            } as Callable<TransferResult>)
        }

        when: "All threads fire at the exact same moment"
        startLatch.countDown()
        List<Future<TransferResult>> futures = executor.invokeAll(tasks)
        executor.shutdown()

        then: "Every transfer completes successfully without exceptions or deadlocks"
        futures.each { future ->
            assert future.get().status() == TransferResult.Status.COMPLETED
        }

        and: "Total invariant balance remains exactly 10000.00 PLN"
        BigDecimal balanceA = getAccountBalance("conc-acc-A")
        BigDecimal balanceB = getAccountBalance("conc-acc-B")

        balanceA == new BigDecimal("5000.0000")
        balanceB == new BigDecimal("5000.0000")
        balanceA.add(balanceB) == new BigDecimal("10000.0000")

        and: "Double-entry ledger records exactly 200 debit/credit rows"
        int totalLedgerEntries = getLedgerEntriesCount()
        totalLedgerEntries == totalTransfers * 2
    }

    private void executeSql(String sql) {
        try (Connection conn = dbConfig.getDataSource().getConnection();
             def stmt = conn.createStatement()) {
            conn.setAutoCommit(true)
            stmt.execute(sql)
        }
    }

    private BigDecimal getAccountBalance(String accountId) {
        try (Connection conn = dbConfig.getDataSource().getConnection();
             def stmt = conn.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            stmt.setString(1, accountId)
            def rs = stmt.executeQuery()
            rs.next()
            return rs.getBigDecimal("balance")
        }
    }

    private int getLedgerEntriesCount() {
        try (Connection conn = dbConfig.getDataSource().getConnection();
             def stmt = conn.createStatement();
             def rs = stmt.executeQuery("SELECT count(*) FROM ledger_entries WHERE account_id IN ('conc-acc-A', 'conc-acc-B')")) {
            rs.next()
            return rs.getInt(1)
        }
    }
}