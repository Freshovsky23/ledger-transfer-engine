package com.fintech.ledger.domain

import spock.lang.Specification

class AccountSpec extends Specification {

    def "should throw exception when creating account with negative balance"() {
        when: "Creating account with negative balance"
        new Account("acc-1", "user-1", "PLN", new BigDecimal("-10.00"))

        then: "IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }

    def "should correctly debit amount when funds are sufficient"() {
        given: "Account with 1000.00 PLN balance"
        def account = new Account("acc-1", "user-1", "PLN", new BigDecimal("1000.00"))

        when: "Debiting 250.50 PLN"
        def updated = account.debit(new BigDecimal("250.50"))

        then: "Balance decreases accurately"
        updated.balance() == new BigDecimal("749.50")
    }

    def "should throw IllegalStateException when debiting more than balance"() {
        given: "Account with 100.00 PLN balance"
        def account = new Account("acc-1", "user-1", "PLN", new BigDecimal("100.00"))

        when: "Debiting 150.00 PLN"
        account.debit(new BigDecimal("150.00"))

        then: "IllegalStateException is thrown"
        thrown(IllegalStateException)
    }

    def "should correctly credit amount to account"() {
        given: "Account with 100.00 PLN"
        def account = new Account("acc-1", "user-1", "PLN", new BigDecimal("100.00"))

        when: "Crediting 50.00 PLN"
        def updated = account.credit(new BigDecimal("50.00"))

        then: "Balance increases accurately"
        updated.balance() == new BigDecimal("150.00")
    }
}