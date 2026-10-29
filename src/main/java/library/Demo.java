package library;

import library.Models.*;

import java.time.LocalDate;
import java.util.List;

/** 演示脚本：清库 → 建表 → 灌数据 → 借书 / 逾期 / 预约 / 报表，输出完整流程。 */
public final class Demo {

    private Demo() { }

    public static void run() throws Exception {
        resetDatabase();
        SampleData.seed();

        LibraryService svc = new LibraryService();
        ReportService report = new ReportService();
        LocalDate today = LocalDate.now();

        step("图书列表");
        Table.print(new String[]{ "ID", "书名", "作者", "分类", "库存", "可借" },
                svc.searchBooks(null).stream().map(b -> new String[]{
                        String.valueOf(b.id), Table.fit(b.title, 22), Table.fit(b.author, 20),
                        b.category, String.valueOf(b.total), String.valueOf(b.available)
                }).toList(),
                new boolean[]{ true, false, false, false, true, true });

        step("借书：张三 借《算法导论》");
        say(svc.borrow(2, 1, today));

        step("借书：李四 借《深入理解计算机系统》");
        say(svc.borrow(1, 2, today));

        step("再借一次同样的书（应被拒绝）");
        say(svc.borrow(1, 2, today));

        step("还书：李四 逾期 12 天归还");
        say(svc.giveBack(1, 2, today.plusDays(42)));

        step("预约：《图解 HTTP》只有 1 册，被借走后排队");
        say(svc.borrow(4, 3, today));
        say("王五 预约：" + svc.reserve(4, 4).message);
        say("张三 预约：" + svc.reserve(4, 1).message);

        step("报表：分类借阅量");
        Table.print(new String[]{ "分类", "借阅次数", "品种数" }, report.loansByCategory(),
                new boolean[]{ false, true, true });

        step("报表：当前逾期名单");
        Table.print(new String[]{ "读者", "电话", "书名", "应还", "逾期", "罚金" },
                report.overdue(today.plusDays(42)), new boolean[]{ false, false, false, false, true, true });

        step("导出热门图书 CSV");
        System.out.println("已导出：" + report.exportTopBooksCsv("top_books.csv", 10));
        System.out.println(java.nio.file.Files.readString(java.nio.file.Path.of("top_books.csv")).replace("\uFEFF", ""));
    }

    /** 清库重建，保证每次演示结果一致。 */
    public static void resetDatabase() throws Exception {
        try (var c = Db.connectServer(); var st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + Db.databaseName() + "`");
        }
        Db.initSchema();
    }

    private static void say(LoanResult r) { say((r.ok ? "✔ " : "✘ ") + r.message); }

    private static void say(String s) { System.out.println(s); }

    private static void step(String title) {
        System.out.println();
        System.out.println("▶ " + title);
        System.out.println("─".repeat(Table.width(title) + 2));
    }
}
