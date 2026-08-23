package com.fintech.ledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.ledger.api.dto.CreateTransferRequest;
import com.fintech.ledger.api.dto.TransferResponse;
import com.fintech.ledger.domain.Account;
import com.fintech.ledger.domain.TransferCommand;
import com.fintech.ledger.domain.TransferResult;
import com.fintech.ledger.service.TransferService;
import spark.Request;
import spark.Response;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static spark.Spark.*;

public class TransferController {

    private final TransferService transferService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final JsonTransformer jsonTransformer;

    public TransferController(TransferService transferService, DataSource dataSource) {
        this.transferService = transferService;
        this.dataSource = dataSource;
        this.jsonTransformer = new JsonTransformer();
        this.objectMapper = JsonTransformer.getMapper();
    }

    public void registerRoutes() {
        before((req, res) -> res.type("application/json"));

        // Health check
        get("/health", (req, res) -> Map.of("status", "UP"), jsonTransformer);

        // Pobieranie salda konta
        get("/accounts/:id/balance", this::getAccountBalance, jsonTransformer);

        // Wykonanie transferu
        post("/transfers", this::handleTransfer, jsonTransformer);

        // Mapowanie błędów biznesowych i walidacji
        exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.body("{\"error\": \"" + e.getMessage() + "\"}");
        });

        exception(IllegalStateException.class, (e, req, res) -> {
            res.status(422);
            res.body("{\"error\": \"" + e.getMessage() + "\"}");
        });

        exception(Exception.class, (e, req, res) -> {
            res.status(500);
            res.body("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        });
    }

    private Object getAccountBalance(Request req, Response res) throws Exception {
        String accountId = req.params(":id");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, user_id, currency, balance FROM accounts WHERE id = ?")) {
            stmt.setString(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("currency"),
                            rs.getBigDecimal("balance")
                    );
                }
            }
        }
        res.status(404);
        return Map.of("error", "Account not found: " + accountId);
    }

    private TransferResponse handleTransfer(Request req, Response res) throws Exception {
        String idempotencyKey = req.headers("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            res.status(400);
            throw new IllegalArgumentException("Header 'Idempotency-Key' is mandatory");
        }

        CreateTransferRequest body = objectMapper.readValue(req.body(), CreateTransferRequest.class);

        TransferCommand command = new TransferCommand(
                idempotencyKey,
                body.sourceAccountId(),
                body.destinationAccountId(),
                body.amount(),
                body.currency()
        );

        TransferResult result = transferService.executeTransfer(command);
        res.status(200);
        return new TransferResponse(result.status().name(), result.message(), result.transferId());
    }
}