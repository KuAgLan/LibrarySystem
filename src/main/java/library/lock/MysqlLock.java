package library.lock;

import library.Db;

import java.sql.*;
import java.time.Duration;
import java.util.UUID;

/**
 * 基于 MySQL 的分布式锁：一行记录代表一把锁，靠主键唯一 + 条件更新做到互斥。
 *
 * <pre>
 * lock_guard(name PK, owner, acquired_at, expires_at)
 * 抢锁：INSERT ... ON DUPLICATE KEY UPDATE（只有过期的行才允许被改写）
 * 释放：DELETE WHERE name=? AND owner=?
 * 续期：UPDATE ... WHERE name=? AND owner=?
 * </pre>
 *
 * 优点：不引入额外中间件，跟业务库同一个事务体系；
 * 缺点：锁的可用性依赖数据库，主库挂了锁就没了；高频抢锁会给库带来写压力。
 */
public class MysqlLock implements DistributedLock {

    private final String owner = UUID.randomUUID().toString();

    public MysqlLock() {
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = Db.connect(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lock_guard (
                    name        VARCHAR(64)  NOT NULL,
                    owner       VARCHAR(64)  NOT NULL,
                    acquired_at DATETIME(3)  NOT NULL,
                    expires_at  DATETIME(3)  NOT NULL,
                    PRIMARY KEY (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 lock_guard 表失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean tryLock(String name, Duration ttl) {
        // 一条语句完成"抢锁"：行不存在就插入；存在且已过期就覆盖；未过期则原样保留。
        // MySQL 的赋值按从左到右执行，后面的 IF 读到的仍是旧的 expires_at，所以顺序不能改。
        String sql = """
            INSERT INTO lock_guard(name, owner, acquired_at, expires_at)
            VALUES(?, ?, NOW(3), NOW(3) + INTERVAL ? MICROSECOND)
            ON DUPLICATE KEY UPDATE
                owner       = IF(expires_at < NOW(3), VALUES(owner), owner),
                acquired_at = IF(expires_at < NOW(3), VALUES(acquired_at), acquired_at),
                expires_at  = IF(expires_at < NOW(3), VALUES(expires_at), expires_at)
            """;
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, owner);
            ps.setLong(3, ttl.toMillis() * 1000L);
            ps.executeUpdate();
            return owner.equals(holder(name));
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public boolean renew(String name, Duration ttl) {
        String sql = "UPDATE lock_guard SET expires_at = NOW(3) + INTERVAL ? MICROSECOND "
                + "WHERE name = ? AND owner = ?";
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ttl.toMillis() * 1000L);
            ps.setString(2, name);
            ps.setString(3, owner);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public boolean unlock(String name) {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM lock_guard WHERE name=? AND owner=?")) {
            ps.setString(1, name);
            ps.setString(2, owner);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String holder(String name) {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT owner FROM lock_guard WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public void close() {
        // 每个操作都是独立连接，无需关闭资源
    }
}
