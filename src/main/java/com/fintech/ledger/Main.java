package com.fintech.ledger;

import com.fintech.ledger.api.TransferController;
import com.fintech.ledger.config.DatabaseConfig;
import com.fintech.ledger.repository.AccountRepository;
import com.fintech.ledger.repository.LedgerRepository;
import com.fintech.ledger.repository.PostgresAccountRepository;
import com.fintech.ledger.repository.PostgresLedgerRepository;
import com.fintech.ledger.service.IdempotencyService;
import com.fintech.ledger.service.RedisIdempotencyService;
import com.fintech.ledger.service.TransferService;

import static spark.Spark.port;

public class Main {
    public static void main(String[] args) {
        port(8080);

        // Inicjalizacja puli połączeń do Postgresa i Redisa
        DatabaseConfig dbConfig = new DatabaseConfig();

        // Składanie zależności
        AccountRepository accountRepo = new PostgresAccountRepository();
        LedgerRepository ledgerRepo = new PostgresLedgerRepository();
        IdempotencyService idempotencyService = new RedisIdempotencyService(dbConfig.getJedisPool(), 3600);

        TransferService transferService = new TransferService(
                dbConfig.getDataSource(),
                accountRepo,
                ledgerRepo,
                idempotencyService
        );

        // Uruchomienie tras HTTP
        TransferController controller = new TransferController(transferService, dbConfig.getDataSource());
        controller.registerRoutes();

        System.out.println("=================================================");
        System.out.println(" Ledger Transfer Engine is running on port 8080 ");
        System.out.println("=================================================");
    }
}