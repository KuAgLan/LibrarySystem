package com.library.lock;

import java.time.Duration;

/**
 * 分布式锁：保证多实例部署时同一任务只执行一次。
 * 两套实现：MySQL（lock_guard 表，默认）与 Redis（SET NX PX + Lua 释放）。
 */
public interface DistributedLock {

    /** 实现名称，便于日志与前端展示 */
    String name();

    /**
     * 尝试获取锁。
     *
     * @param key 业务键
     * @param ttl 租约时长，到期自动释放，避免持有者崩溃造成死锁
     * @return true 表示获取成功
     */
    boolean tryLock(String key, Duration ttl);

    /** 续租（看门狗）：持有者定期延长租约 */
    boolean renew(String key, Duration ttl);

    /** 释放锁：仅持有者可释放 */
    boolean unlock(String key);

    /** 便捷执行：拿不到锁直接返回 false，不抛异常 */
    default boolean runIfLocked(String key, Duration ttl, Runnable action) {
        if (!tryLock(key, ttl)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlock(key);
        }
    }
}