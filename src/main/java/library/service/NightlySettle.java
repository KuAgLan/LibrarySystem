package library.service;

import library.Db;
import library.lock.DistributedLock;
import library.LibraryService;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日结算任务：统计逾期、刷新罚金预估、写结算审计。
 *
 * 这个任务被定时器触发，多台应用服务器会同时到点，所以必须"同一时刻只有一个实例在跑"，
 * 用分布式锁把它包起来：拿不到锁就直接跳过（不是排队，避免堆积）。
 *
 * 长任务还要续期（看门狗）：业务没跑完但锁到期了，别的实例就会进来，等于锁白加了。
 */
public class NightlySettle {

    public static final String LOCK_KEY = "settle:nightly";

    public static class Outcome {
        public final boolean ran;
        public final String message;
        public final long elapsedMs;
        public final String holder;

        Outcome(boolean ran, String message, long elapsedMs, String holder) {
            this.ran = ran;
            this.message = message;
            this.elapsedMs = elapsedMs;
            this.holder = holder;
        }
    }

    /**
     * @param lock   分布式锁实现
     * @param ttl    锁的过期时间（建议大于任务耗时，这里靠看门狗续期）
     * @param workMs 模拟任务耗时
     */
    public static Outcome run(DistributedLock lock, Duration ttl, long workMs) {
        long start = System.currentTimeMillis();

        if (!lock.tryLock(LOCK_KEY, ttl)) {
            return new Outcome(false,
                    "跳过：锁被其他实例占用（holder=" + shortId(lock.holder(LOCK_KEY)) + "）",
                    0, lock.holder(LOCK_KEY));
        }

        AtomicBoolean lost = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            long interval = Math.max(200, ttl.toMillis() / 3);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    return;
                }
                if (!lock.renew(LOCK_KEY, ttl)) lost.set(true);   // 锁被别人接管了，任务该中止
            }
        }, "lock-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        String summary;
        try {
            summary = doSettle(workMs, lost);
        } catch (Exception e) {
            summary = "结算失败：" + e.getMessage();
        } finally {
            watchdog.interrupt();
            lock.unlock(LOCK_KEY);
        }

        long elapsed = System.currentTimeMillis() - start;
        return new Outcome(true, summary, elapsed, lock.owner());
    }

    private static String doSettle(long workMs, AtomicBoolean lost) throws Exception {
        LocalDate today = LocalDate.now();
        int overdue = 0;
        double fineSum = 0;

        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT due_at FROM loans WHERE returned_at IS NULL AND due_at < ?")) {
            ps.setDate(1, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(rs.getDate(1).toLocalDate(), today);
                    overdue++;
                    fineSum += days * LibraryService.FINE_PER_DAY;
                }
            }
        }

        // 模拟一个比较慢的结算过程（真实场景可能是导出报表、给几百个读者发提醒）
        long step = 100;
        long waited = 0;
        while (waited < workMs) {
            Thread.sleep(step);
            waited += step;
            if (lost.get()) throw new IllegalStateException("锁已失效，中止任务");
        }

        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_log(at, action, detail) VALUES(?, 'SETTLE_NIGHTLY', ?)")) {
            ps.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setString(2, "overdue=" + overdue + " fine=" + String.format("%.2f", fineSum));
            ps.executeUpdate();
        }

        return String.format("完成：逾期 %d 册，预估罚金 %.2f 元（实例 %s）",
                overdue, fineSum, shortId(null));
    }

    private static String shortId(String id) {
        if (id == null) return "self";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
