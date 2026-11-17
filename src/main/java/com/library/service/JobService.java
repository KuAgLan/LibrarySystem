package com.library.service;

import com.library.common.BizException;
import com.library.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 任务调度入口：定时任务统一走分布式锁，保证多实例只跑一次 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    public static final String LOCK_NIGHTLY = "job:nightly-settle";

    private final DistributedLock distributedLock;
    private final NightlySettleService nightlySettleService;

    @Value("${app.lock.ttl-seconds:60}")
    private long lockTtlSeconds;

    public Map<String, Object> runNightlySettle() {
        return runNightlySettle(0);
    }

    /**
     * @param holdMs 额外持有锁的毫秒数（仅用于验证并发互斥，上限 10 秒）
     */
    public Map<String, Object> runNightlySettle(long holdMs) {
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        boolean locked = distributedLock.runIfLocked(LOCK_NIGHTLY, Duration.ofSeconds(lockTtlSeconds), () -> {
            try (Renewer renewer = new Renewer(distributedLock, LOCK_NIGHTLY, Duration.ofSeconds(lockTtlSeconds))) {
                result.set(nightlySettleService.settle());
                long hold = Math.min(10_000L, Math.max(0L, holdMs));
                if (hold > 0) {
                    try {
                        Thread.sleep(hold);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        if (!locked) {
            throw new BizException(409, "任务正在其它实例执行中（锁 " + LOCK_NIGHTLY + " 被占用），请稍后重试");
        }
        Map<String, Object> summary = new LinkedHashMap<>(result.get());
        summary.put("lockedBy", distributedLock.name());
        return summary;
    }

    public String provider() {
        return distributedLock.name();
    }

    /** 看门狗：按 1/3 租约周期后台续租，避免长任务被误抢占 */
    private static final class Renewer implements AutoCloseable {

        private final Thread thread;
        private volatile boolean running = true;

        Renewer(DistributedLock lock, String key, Duration ttl) {
            long period = Math.max(5000L, ttl.toMillis() / 3);
            thread = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(period);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!running) {
                        return;
                    }
                    if (!lock.renew(key, ttl)) {
                        log.warn("续租失败，锁可能已被抢占：{}", key);
                        return;
                    }
                }
            }, "lock-watchdog");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            running = false;
            thread.interrupt();
        }
    }
}