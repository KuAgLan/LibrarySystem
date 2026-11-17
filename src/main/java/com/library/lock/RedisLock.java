package com.library.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 实现：SET key owner NX PX ttl 获取，Lua 脚本校验 owner 后释放/续期。
 * 启用方式：app.lock.provider=redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.lock.provider", havingValue = "redis")
public class RedisLock implements DistributedLock {

    private static final String PREFIX = "library:lock:";

    /** 释放：仅 owner 匹配才删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    /** 续期：仅 owner 匹配才延长 */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ThreadLocal<String> owner = ThreadLocal.withInitial(
            () -> UUID.randomUUID().toString().replace("-", ""));

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(PREFIX + key, owner.get(), ttl);
        log.debug("Redis 获取锁 {}：{}", key, ok);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean renew(String key, Duration ttl) {
        Long result = redisTemplate.execute(RENEW_SCRIPT,
                List.of(PREFIX + key), owner.get(), String.valueOf(ttl.toMillis()));
        return result != null && result > 0;
    }

    @Override
    public boolean unlock(String key) {
        Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + key), owner.get());
        return result != null && result > 0;
    }
}