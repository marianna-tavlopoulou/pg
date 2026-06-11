package com.marianna.gateway.adapter;

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
    private static final String KEY_PREFIX = "velocity:";
    private static final Duration KEY_TTL = Duration.ofHours(1);

    @Override
    public int getTransactionCounts(String customerId, Duration window) {

        String key = KEY_PREFIX + customerId;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.toMillis();

        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);

        return count == null ? 0 : count.intValue();
    }

    @Override
    public void recordTransaction(String customerId, String transactionId) {

        String key = KEY_PREFIX + customerId;
        long now = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(key, transactionId, now);
        // Expire old entries and set TTL so keys don't leak forever
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - KEY_TTL.toMillis());
        redisTemplate.expire(key, KEY_TTL);

    }

}
