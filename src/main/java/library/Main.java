package library;

import library.lock.Locks;
import library.service.NightlySettle;

/** 入口：解析参数并分发。 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String url = arg(args, "--url", null);
        String user = arg(args, "--user", null);
        String pass = arg(args, "--password", null);

        // 也支持环境变量，方便 CI / 换机器
        if (url == null) url = System.getenv("LIB_URL");
        if (user == null) user = System.getenv("LIB_USER");
        if (pass == null) pass = System.getenv("LIB_PASSWORD");
        Db.configure(url, user, pass);

        if (has(args, "--help") || has(args, "-h")) {
            System.out.println("""
                图书管理系统（Java + JDBC + MySQL）

                用法：
                  java -jar library-system.jar                     交互式菜单
                  java -jar library-system.jar --init-db           只建库建表
                  java -jar library-system.jar --seed              灌示例数据
                  java -jar library-system.jar --demo              演示：借书 → 逾期 → 预约 → 报表
                  java -jar library-system.jar --selftest          17 项业务规则自测
                  java -jar library-system.jar --report out.csv    导出热门图书报表
                  java -jar library-system.jar --settle            每日结算（分布式锁保护，多实例只跑一个）
                    --lock=mysql|redis  --redis=127.0.0.1:6379  --settle-ms=2000  --lock-ttl=5000
                  java -jar library-system.jar --reset             删库重建（危险）

                连接参数（也可用环境变量 LIB_URL / LIB_USER / LIB_PASSWORD）：
                  --url      jdbc:mysql://127.0.0.1:3306/library?useSSL=false&serverTimezone=Asia/Shanghai
                  --user     root
                  --password ******
                """);
            return;
        }

        Locks.configure(arg(args, "--lock", null), arg(args, "--redis", null));

        if (has(args, "--settle")) {
            long workMs = Long.parseLong(arg(args, "--settle-ms", "2000"));
            long ttlMs = Long.parseLong(arg(args, "--lock-ttl", "5000"));
            Db.initSchema();
            var outcome = NightlySettle.run(Locks.create(), java.time.Duration.ofMillis(ttlMs), workMs);
            System.out.printf("[%s] 结算任务 %s%n",
                    Locks.describe(),
                    outcome.ran ? "执行了" : "没执行");
            System.out.println("  " + outcome.message);
            if (outcome.ran) System.out.println("  耗时 " + outcome.elapsedMs + " ms");
            return;
        }

        if (has(args, "--selftest")) {
            System.exit(SelfTest.run());
        }

        if (has(args, "--reset")) { Demo.resetDatabase(); System.out.println("数据库已重建"); return; }
        if (has(args, "--init-db")) { Db.initSchema(); System.out.println("建库建表完成：" + Db.url()); return; }
        if (has(args, "--demo")) { Demo.run(); return; }
        if (has(args, "--seed")) { Db.initSchema(); SampleData.seed(); System.out.println("示例数据已写入"); return; }
        if (has(args, "--report")) {
            Db.initSchema();
            System.out.println("已导出：" + new ReportService().exportTopBooksCsv(arg(args, "--report", "top_books.csv"), 10));
            return;
        }

        Db.initSchema();
        new Menu().run();
    }

    private static boolean has(String[] args, String flag) {
        for (String a : args) if (a.equalsIgnoreCase(flag)) return true;
        return false;
    }

    /** 同时支持 --key value 和 --key=value 两种写法。 */
    private static String arg(String[] args, String flag, String fallback) {
        String prefix = flag.toLowerCase() + "=";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(flag) && i + 1 < args.length) return args[i + 1];
            if (args[i].toLowerCase().startsWith(prefix)) return args[i].substring(prefix.length());
        }
        return fallback;
    }
}
