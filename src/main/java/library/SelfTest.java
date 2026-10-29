package library;

import library.Models.*;
import library.lock.*;
import java.time.Duration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** 业务规则自测：java -cp ... library.Main --selftest */
public final class SelfTest {

    private static int pass;
    private static int fail;

    private SelfTest() { }

    public static int run() throws Exception {
        Demo.resetDatabase();
        LibraryService svc = new LibraryService();

        borrowRules(svc);
        fineRules(svc);
        reservationQueue(svc);
        concurrentBorrow();

        section("分布式锁 · MySQL");
        lockSuite(new MysqlLock());

        if (redisAvailable()) {
            section("分布式锁 · Redis");
            lockSuite(new RedisLock("127.0.0.1:6379", "test-lock:"));
        } else {
            section("分布式锁 · Redis");
            System.out.println("  - 跳过（127.0.0.1:6379 没有可用的 Redis）");
        }

        System.out.println();
        System.out.printf("结果：%d 通过，%d 失败%n", pass, fail);
        return fail == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------ 借阅规则

    private static void borrowRules(LibraryService svc) throws Exception {
        section("借阅规则");
        long book = svc.addBook(book("9787000000001", "算法导论", 1));
        long r1 = svc.addReader(reader("R001", "张三", 2));
        long r2 = svc.addReader(reader("R002", "李四", 5));
        LocalDate today = LocalDate.now();

        LoanResult a = svc.borrow(book, r1, today);
        check("借书成功", a.ok, a.message);
        check("库存扣减到 0", svc.getBook(book).available == 0, "available=" + svc.getBook(book).available);

        LoanResult b = svc.borrow(book, r2, today);
        check("无副本时借阅被拒绝", !b.ok, b.message);

        LoanResult c = svc.borrow(book, r1, today);
        check("同一本书不能重复借", !c.ok, c.message);

        long book2 = svc.addBook(book("9787000000002", "编译原理", 3));
        check("上限内第一册可借", svc.borrow(book2, r1, today).ok, "");
        LoanResult over = svc.borrow(book2, r1, today);
        check("达到借阅上限后被拒", !over.ok, over.message);

        LoanResult back = svc.giveBack(book, r1, today);
        check("归还成功", back.ok, back.message);
        check("归还后库存回补到 1", svc.getBook(book).available == 1, "available=" + svc.getBook(book).available);
    }

    // ------------------------------------------------------------ 罚金

    private static void fineRules(LibraryService svc) throws Exception {
        section("逾期罚金");
        long reader = svc.addReader(reader("R100", "王五", 5));
        long book = svc.addBook(book("9787000000010", "数据库系统概念", 1));
        LocalDate today = LocalDate.now();

        svc.borrow(book, reader, today.minusDays(40));   // 40 天前借，应还日已过 10 天
        LoanResult r = svc.giveBack(book, reader, today);

        double expected = Math.round(10 * LibraryService.FINE_PER_DAY * 100) / 100.0;
        check("逾期 10 天罚金 = " + expected, r.ok && Math.abs(r.fine - expected) < 0.001,
                String.format("%.2f / %s", r.fine, r.message));

        double balance = svc.reader(reader).balance;
        check("罚金记入读者欠费", Math.abs(balance - expected) < 0.001, String.format("%.2f", balance));

        long book2 = svc.addBook(book("9787000000011", "操作系统", 1));
        svc.borrow(book2, reader, today.minusDays(100));
        svc.giveBack(book2, reader, today);              // 逾期 70 天 → 35 元
        double debt = svc.reader(reader).balance;
        check("欠费累计超过 20 元", debt > LibraryService.FINE_BLOCK, String.format("%.2f", debt));

        LoanResult blocked = svc.borrow(book, reader, today);
        check("欠费超限时拒绝借阅", !blocked.ok, blocked.message);
    }

    // ------------------------------------------------------------ 预约队列

    private static void reservationQueue(LibraryService svc) throws Exception {
        section("预约队列");
        long book = svc.addBook(book("9787000000020", "深入理解计算机系统", 1));
        long a = svc.addReader(reader("RA", "甲", 5));
        long b = svc.addReader(reader("RB", "乙", 5));
        long c = svc.addReader(reader("RC", "丙", 5));
        LocalDate today = LocalDate.now();

        svc.borrow(book, a, today);
        check("无可借副本时可以预约", svc.reserve(book, b).ok && svc.reserve(book, c).ok, "");
        check("同一人不能重复预约", !svc.reserve(book, b).ok, "");

        svc.giveBack(book, a, today);

        try (var conn = Db.connect();
             var ps = conn.prepareStatement(
                     "SELECT reader_id FROM reservations WHERE book_id=? AND notified_at IS NOT NULL ORDER BY id LIMIT 1")) {
            ps.setLong(1, book);
            try (var rs = ps.executeQuery()) {
                boolean ok = rs.next() && rs.getLong(1) == b;
                check("归还后通知队列第一位（乙）", ok, "");
            }
        }
    }

    // ------------------------------------------------------------ 分布式锁

    /** 锁的共性断言：互斥、释放、非持有者、TTL 过期、续期。 */
    private static void lockSuite(DistributedLock lock) throws Exception {
        String key = "selftest:" + java.util.UUID.randomUUID();
        Duration ttl = Duration.ofMillis(800);

        check("拿到锁", lock.tryLock(key, ttl), "");
        check("别人拿不到同一把锁", !newOther(lock).tryLock(key, ttl), "");
        check("非持有者释放无效", !newOther(lock).unlock(key), "锁仍在");
        check("持有者能续期", lock.renew(key, ttl), "");
        check("续期后仍归自己", lock.owner().equals(lock.holder(key)), "");
        check("持有者能释放", lock.unlock(key), "");
        DistributedLock other = newOther(lock);
        check("释放后别人能拿到", other.tryLock(key, ttl), "");
        other.unlock(key);

        // TTL 过期后允许抢占
        check("短 TTL 拿到锁", lock.tryLock(key, Duration.ofMillis(250)), "");
        Thread.sleep(400);
        DistributedLock other2 = newOther(lock);
        check("过期后可被他人抢占", other2.tryLock(key, ttl), "");
        other2.unlock(key);
    }

    /** 同一后端的另一个"实例"（模拟另一台服务器）。 */
    private static DistributedLock newOther(DistributedLock like) {
        if (like instanceof MysqlLock) return new MysqlLock();
        return new RedisLock("127.0.0.1:6379", "test-lock:");
    }

    private static boolean redisAvailable() {
        try (RedisLock r = new RedisLock("127.0.0.1:6379", "test-lock:")) {
            return r.tryLock("ping-check", Duration.ofMillis(500));
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ 并发

    private static void concurrentBorrow() throws Exception {
        section("并发借书（10 个线程抢最后 1 册）");
        LibraryService svc = new LibraryService();
        long book = svc.addBook(book("9787000000030", "并发编程实战", 1));

        List<Long> readers = new ArrayList<>();
        for (int i = 0; i < 10; i++) readers.add(svc.addReader(reader("C" + i, "并发读者" + i, 5)));

        var ok = new java.util.concurrent.atomic.AtomicInteger();
        var failed = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers.size());

        for (long rid : readers) {
            new Thread(() -> {
                try {
                    gate.await();
                    LoanResult r = new LibraryService().borrow(book, rid, LocalDate.now());
                    if (r.ok) ok.incrementAndGet(); else failed.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await();

        check("只有 1 个线程借成功", ok.get() == 1, "成功 " + ok.get() + "，失败 " + failed.get());
        check("库存没有变成负数", svc.getBook(book).available == 0, "available=" + svc.getBook(book).available);
    }

    // ------------------------------------------------------------

    private static Book book(String isbn, String title, int total) {
        Book b = new Book();
        b.isbn = isbn;
        b.title = title;
        b.author = "测试作者";
        b.category = "计算机";
        b.total = total;
        b.available = total;
        return b;
    }

    private static Reader reader(String card, String name, int max) {
        Reader r = new Reader();
        r.cardNo = card;
        r.name = name;
        r.phone = "13800000000";
        r.maxLoans = max;
        return r;
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("── " + name + " ──");
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ✔ " + name); }
        else { fail++; System.out.println("  ✘ " + name + (detail == null || detail.isEmpty() ? "" : "  → " + detail)); }
    }
}
