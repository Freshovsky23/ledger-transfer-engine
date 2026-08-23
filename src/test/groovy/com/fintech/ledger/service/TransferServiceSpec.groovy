package com.fintech.ledger.service

import com.fintech.ledger.domain.Account
import com.fintech.ledger.domain.TransferCommand
import com.fintech.ledger.domain.TransferResult
import com.fintech.ledger.repository.AccountRepository
import com.fintech.ledger.repository.LedgerRepository
import spock.lang.Specification
import spock.lang.Subject

import javax.sql.DataSource
import java.sql.Connection

class TransferServiceSpec extends Specification {

    DataSource dataSource = Mock()
    Connection connection = Mock()
    AccountRepository accountRepository = Mock()
    LedgerRepository ledgerRepository = Mock()
    IdempotencyService idempotencyService = Mock()

    @Subject
    TransferService transferService = new TransferService(dataSource, accountRepository, ledgerRepository, idempotencyService)

    def setup() {
        dataSource.getConnection() >> connection
    }

    def "should successfully execute a valid transfer between two accounts"() {
        given: "Two accounts in PLN"
        def source = new Account("acc-1", "user-1", "PLN", new BigDecimal("1000.00"))
        def destination = new Account("acc-2", "user-2", "PLN", new BigDecimal("200.00"))
        def command = new TransferCommand("idem-key-123", "acc-1", "acc-2", new BigDecimal("300.00"), "PLN")

        and: "Idempotency check passes"
        idempotencyService.isProcessed("idem-key-123") >> false

        and: "Repository locks and returns the accounts"
        accountRepository.findByIdForUpdate(connection, "acc-1") >> Optional.of(source)
        accountRepository.findByIdForUpdate(connection, "acc-2") >> Optional.of(destination)

        when: "Transfer is executed"
        TransferResult result = transferService.executeTransfer(command)

        then: "Transfer is marked as COMPLETED and committed"
        result.status() == TransferResult.Status.COMPLETED
        1 * connection.commit()
        1 * ledgerRepository.createTransfer(connection, _, command)
        1 * ledgerRepository.recordDoubleEntry(connection, _, command)
        1 * idempotencyService.markProcessed("idem-key-123", _)
    }

    def "should rollback transaction when source account has insufficient funds"() {
        given: "Source account with low balance"
        def source = new Account("acc-1", "user-1", "PLN", new BigDecimal("50.00"))
        def destination = new Account("acc-2", "user-2", "PLN", new BigDecimal("200.00"))
        def command = new TransferCommand("idem-key-456", "acc-1", "acc-2", new BigDecimal("100.00"), "PLN")

        idempotencyService.isProcessed("idem-key-456") >> false
        accountRepository.findByIdForUpdate(connection, "acc-1") >> Optional.of(source)
        accountRepository.findByIdForUpdate(connection, "acc-2") >> Optional.of(destination)

        when: "Transfer is attempted"
        transferService.executeTransfer(command)

        then: "IllegalStateException is thrown and transaction is rolled back"
        thrown(IllegalStateException)
        1 * connection.rollback()
        0 * connection.commit()
    }

    def "should return cached result when duplicate idempotency key is submitted"() {
        given: "An already processed idempotency key"
        def command = new TransferCommand("duplicate-key-789", "acc-1", "acc-2", new BigDecimal("50.00"), "PLN")
        def cachedResult = TransferResult.cached("tx-existing-1")

        idempotencyService.isProcessed("duplicate-key-789") >> true
        idempotencyService.getCachedResult("duplicate-key-789") >> Optional.of(cachedResult)

        when: "Duplicate command arrives"
        def result = transferService.executeTransfer(command)

        then: "Cached result is returned without database connection"
        result == cachedResult
        0 * dataSource.getConnection()
    }
}