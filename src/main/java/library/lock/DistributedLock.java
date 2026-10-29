package library.lock;

import java.time.Duration;

/**
 * 分布式锁的最小接口。
 * 语义：带 TTL 的互斥；只有持有者能续期 / 释放；过期后允许被别人抢占。
 */
public interface DistributedLock extends AutoCloseable {

    /** 尝试获取锁，成功返回 true（不阻塞）。 */
    boolean tryLock(String name, Duration ttl);

    /** 续期，只有当前持有者才能成功。 */
    boolean renew(String name, Duration ttl);

    /** 释放，只有当前持有者才能成功。 */
    boolean unlock(String name);

    /** 本客户端的持有者标识（UUID），用于排查"锁是谁拿的"。 */
    String owner();

    /** 当前锁的持有者（用于日志/演示），没有锁返回 null。 */
    String holder(String name);

    @Override
    void close();
}
