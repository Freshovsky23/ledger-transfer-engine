-- Typy wyliczeniowe
CREATE TYPE entry_direction AS ENUM ('DEBIT', 'CREDIT');
CREATE TYPE transfer_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

-- Tabela kont
CREATE TABLE accounts (
                          id VARCHAR(36) PRIMARY KEY,
                          user_id VARCHAR(36) NOT NULL,
                          currency VARCHAR(3) NOT NULL,
                          balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT chk_positive_balance CHECK (balance >= 0.0000)
);

-- Tabela transferów
CREATE TABLE transfers (
                           id VARCHAR(36) PRIMARY KEY,
                           idempotency_key VARCHAR(128) UNIQUE NOT NULL,
                           source_account_id VARCHAR(36) NOT NULL REFERENCES accounts(id),
                           destination_account_id VARCHAR(36) NOT NULL REFERENCES accounts(id),
                           amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
                           currency VARCHAR(3) NOT NULL,
                           status transfer_status NOT NULL,
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela wpisów księgowych (Double-Entry)
CREATE TABLE ledger_entries (
                                id VARCHAR(36) PRIMARY KEY,
                                transfer_id VARCHAR(36) NOT NULL REFERENCES transfers(id),
                                account_id VARCHAR(36) NOT NULL REFERENCES accounts(id),
                                amount NUMERIC(18, 4) NOT NULL,
                                direction entry_direction NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indeksy wydajnościowe
CREATE INDEX idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX idx_transfers_idempotency ON transfers(idempotency_key);

-- Konta startowe do testów lokalnych
INSERT INTO accounts (id, user_id, currency, balance) VALUES
                                                          ('acc-pln-1', 'user-100', 'PLN', 10000.0000),
                                                          ('acc-pln-2', 'user-200', 'PLN', 500.0000),
                                                          ('acc-eur-1', 'user-100', 'EUR', 2500.0000);