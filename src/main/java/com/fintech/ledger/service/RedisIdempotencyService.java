package com.fintech.ledger.service;

import com.fintech.ledger.domain.TransferResult;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.Optional;

public class RedisIdempotencyService implements IdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private final JedisPool jedisPool;
    private final long ttlSeconds;

    public RedisIdempotencyService(JedisPool jedisPool, long ttlSeconds) {
        this.jedisPool = jedisPool;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public boolean isProcessed(String idempotencyKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(IDEMPOTENCY_PREFIX + idempotencyKey);
        }
    }

    @Override
    public Optional<TransferResult> getCachedResult(String idempotencyKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            String transferId = jedis.get(IDEMPOTENCY_PREFIX + idempotencyKey);
            if (transferId != null) {
                return Optional.of(TransferResult.cached(transferId));
            }
            return Optional.empty();
        }
    }

    @Override
    public void markProcessed(String idempotencyKey, TransferResult result) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = IDEMPOTENCY_PREFIX + idempotencyKey;
            String val = result.transferId() != null ? result.transferId() : "COMPLETED";
            jedis.set(key, val, SetParams.setParams().ex(ttlSeconds));
        }
    }
}