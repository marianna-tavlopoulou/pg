package com.marianna.gateway.adapter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.marianna.gateway.port.VelocityCheckPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisVelocityCheckAdapter implements VelocityCheckPort {

    private final StringRedisTemplate redisTemplate;
    private static final String VELOCITY_KEY = "velocity:%s";
    private static final String AMOUNT_KEY = "amt_velocity:%s";
    private static final int MAX_TXN = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Override
    public int getTransactionCounts(String customerId, Duration window) {

        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.toMillis();

        Long count = redisTemplate.opsForZSet().count(key(VELOCITY_KEY, customerId), windowStart, now);

        return count == null ? 0 : count.intValue();
    }

    @Override
    public void recordTransaction(String customerId, String transactionId, BigDecimal amount) {

        long now = Instant.now().toEpochMilli();

        String vKey = key(VELOCITY_KEY, customerId);
        redisTemplate.opsForZSet().add(vKey, transactionId, now);
        // Expire old entries and set TTL so keys don't leak forever
        redisTemplate.opsForZSet().removeRangeByScore(vKey, 0, now - WINDOW.toMillis());
        redisTemplate.expire(vKey, WINDOW.plusMinutes(1)); // The TTL is WINDOW.plusMinutes(1) not WINDOW. This is
                                                           // subtle but important: if a transaction arrives at t=0 and
                                                           // the TTL is exactly 5 minutes, Redis may evict the key at
                                                           // t=5:00 — but a new transaction at t=4:59 should still be
                                                           // counted against it. The 1-minute buffer ensures the key
                                                           // survives long enough for the window to naturally expire.
                                                           // The removeRangeByScore handles the logical eviction of old
                                                           // entries; the TTL is just a safety net against key leakage.
        String aKey = key(AMOUNT_KEY, customerId) + ":" + amount.toPlainString();
        redisTemplate.opsForZSet().add(aKey, transactionId, now);
        redisTemplate.opsForZSet().removeRangeByScore(aKey, 0, WINDOW.toMillis());
        redisTemplate.expire(aKey, WINDOW.plusMinutes(1));

    }

    @Override
    public boolean isVelocityExceeded(String customerId) {

        return getTransactionCounts(customerId, WINDOW) >= MAX_TXN;
    }

    @Override
    public int getDuplicateAmountCount(String customerId, BigDecimal amount) {

        long now = Instant.now().toEpochMilli();
        long windowStart = WINDOW.toMillis();

        // Key encodes both customerId and the exact amount
        String amountKey = key(AMOUNT_KEY, customerId) + ":" + amount.toPlainString();
        Long count = redisTemplate.opsForZSet().count(amountKey, windowStart, now);
        return count == null ? 0 : count.intValue();

    }

    private String key(String template, String customerId) {
        return template.formatted(customerId);
    }

}
