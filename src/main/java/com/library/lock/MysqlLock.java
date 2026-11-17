package com.library.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MySQL 实现：lock_guard 表 + 主键唯一约束 + 到期抢占。
 * 不依赖额外中间件，适合只有数据库的环境。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.lock.provider", havingValue = "mysql", matchIfMissing = true)
public class MysqlLock implements DistributedLock {

    private final JdbcTemplate jdbcTemplate;

    /** 持有者标识，用于校验释放权限 */
    private final ThreadLocal<String> owner = ThreadLocal.withInitial(
            () -> UUID.randomUUID().toString().replace("-", ""));

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public boolean tryLock(String key, Duration ttl) {
        String token = owner.get();
        LocalDateTime expireAt = LocalDateTime.now().plus(ttl);
        try {
            jdbcTemplate.update(
                    "INSERT INTO lock_guard (lock_name, owner, expire_at) VALUES (?, ?, ?)",
                    key, token, expireAt);
            log.debug("获取锁成功（新增）：{} owner={}", key, token);
            return true;
        } catch (DuplicateKeyException e) {
            // 已被占用：若已过期则抢占
            int taken = jdbcTemplate.update("""
                    UPDATE lock_guard SET owner = ?, expire_at = ?
                    WHERE lock_name = ? AND expire_at < NOW(3)
                    """, token, expireAt, key);
            if (taken > 0) {
                log.warn("抢占过期锁：{} owner={}", key, token);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean renew(String key, Duration ttl) {
        int rows = jdbcTemplate.update(
                "UPDATE lock_guard SET expire_at = ? WHERE lock_name = ? AND owner = ?",
                LocalDateTime.now().plus(ttl), key, owner.get());
        return rows > 0;
    }

    @Override
    public boolean unlock(String key) {
        int rows = jdbcTemplate.update(
                "DELETE FROM lock_guard WHERE lock_name = ? AND owner = ?", key, owner.get());
        if (rows == 0) {
            log.warn("释放锁失败（已过期或非持有者）：{}", key);
        }
        return rows > 0;
    }

    /** 运维排查用：当前被占用的锁 */
    public List<Map<String, Object>> activeLocks() {
        return jdbcTemplate.queryForList("SELECT lock_name, owner, expire_at FROM lock_guard ORDER BY expire_at");
    }
}