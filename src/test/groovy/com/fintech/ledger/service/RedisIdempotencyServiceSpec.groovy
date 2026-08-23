package com.fintech.ledger.service

import com.fintech.ledger.domain.TransferResult
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.Specification
import spock.lang.Subject

class RedisIdempotencyServiceSpec extends Specification {

    JedisPool jedisPool = Mock()
    Jedis jedis = Mock()

    @Subject
    RedisIdempotencyService service = new RedisIdempotencyService(jedisPool, 3600)

    def setup() {
        jedisPool.getResource() >> jedis
    }

    def "should return true if key exists in Redis"() {
        given:
        jedis.exists("idempotency:test-key") >> true

        expect:
        service.isProcessed("test-key") == true
    }

    def "should retrieve cached TransferResult when key exists"() {
        given:
        jedis.get("idempotency:tx-100") >> "tx-uuid-999"

        when:
        def result = service.getCachedResult("tx-100")

        then:
        result.isPresent()
        result.get().transferId() == "tx-uuid-999"
    }

    def "should store key in Redis with TTL expiration"() {
        given:
        def result = TransferResult.success("new-tx-id")

        when:
        service.markProcessed("new-key", result)

        then:
        1 * jedis.set("idempotency:new-key", "new-tx-id", _)
    }
}