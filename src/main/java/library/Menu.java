package library;

import library.Models.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/** 交互式菜单。 */
public class Menu {

    private final LibraryService svc = new LibraryService();
    private final ReportService report = new ReportService();
    private final Scanner in = new Scanner(System.in);

    public void run() throws Exception {
        while (true) {
            System.out.println();
            System.out.println("══════════ 图书管理系统 ══════════");
            System.out.println("  1. 图书管理      2. 读者管理");
            System.out.println("  3. 借书 / 还书   4. 预约");
            System.out.println("  5. 统计报表      0. 退出");
            System.out.print("请选择：");
            String choice = in.nextLine().trim();
            switch (choice) {
                case "1" -> bookMenu();
                case "2" -> readerMenu();
                case "3" -> loanMenu();
                case "4" -> reserveMenu();
                case "5" -> reportMenu();
                case "0" -> { return; }
                default -> System.out.println("无效的选项");
            }
        }
    }

    // ------------------------------------------------------------ 图书

    private void bookMenu() throws Exception {
        System.out.print("关键字（回车列出全部）：");
        String kw = in.nextLine().trim();
        List<Book> books = svc.searchBooks(kw.isEmpty() ? null : kw);

        System.out.println();
        Table.print(new String[]{ "ID", "ISBN", "书名", "作者", "分类", "库存", "可借" },
                books.stream().map(b -> new String[]{
                        String.valueOf(b.id), b.isbn, Table.fit(b.title, 22), Table.fit(b.author, 12),
                        Table.fit(b.category, 8), String.valueOf(b.total), String.valueOf(b.available)
                }).toList(),
                new boolean[]{ true, false, false, false, false, true, true });

        System.out.println();
        System.out.println("  a 新增  e 修改  d 删除  回车返回");
        System.out.print("操作：");
        switch (in.nextLine().trim()) {
            case "a" -> addBook();
            case "e" -> editBook();
            case "d" -> {
                System.out.print("要删除的图书 ID：");
                long id = readInt();
                svc.removeBook(id);
                System.out.println("已删除（历史借阅记录保留）");
            }
        }
    }

    private void addBook() throws Exception {
        Book b = new Book();
        System.out.print("ISBN："); b.isbn = in.nextLine().trim();
        System.out.print("书名："); b.title = in.nextLine().trim();
        System.out.print("作者："); b.author = in.nextLine().trim();
        System.out.print("分类："); b.category = in.nextLine().trim();
        System.out.print("册数："); b.total = (int) readInt();
        b.available = b.total;
        System.out.println("已新增，ID = " + svc.addBook(b));
    }

    private void editBook() throws Exception {
        System.out.print("要修改的图书 ID：");
        Book b = svc.getBook(readInt());
        if (b == null) { System.out.println("图书不存在"); return; }

        System.out.print("书名（" + b.title + "）：");
        String v = in.nextLine().trim();
        if (!v.isEmpty()) b.title = v;
        System.out.print("作者（" + b.author + "）：");
        v = in.nextLine().trim();
        if (!v.isEmpty()) b.author = v;
        System.out.print("分类（" + b.category + "）：");
        v = in.nextLine().trim();
        if (!v.isEmpty()) b.category = v;
        System.out.print("总册数（" + b.total + "）：");
        v = in.nextLine().trim();
        if (!v.isEmpty()) {
            int total = Integer.parseInt(v);
            if (total >= b.borrowed()) {
                b.available = total - b.borrowed();
                b.total = total;
            }
        }
        svc.updateBook(b);
        System.out.println("已更新");
    }

    // ------------------------------------------------------------ 读者

    private void readerMenu() throws Exception {
        List<Reader> readers = svc.readers();
        System.out.println();
        Table.print(new String[]{ "ID", "借书证", "姓名", "电话", "上限", "欠费", "状态" },
                readers.stream().map(r -> new String[]{
                        String.valueOf(r.id), r.cardNo, Table.fit(r.name, 10), r.phone == null ? "" : r.phone,
                        String.valueOf(r.maxLoans), String.format("%.2f", r.balance), r.enabled ? "正常" : "停用"
                }).toList(),
                new boolean[]{ true, false, false, false, true, true, false });

        System.out.println();
        System.out.println("  a 新增读者  p 缴纳罚金  回车返回");
        System.out.print("操作：");
        switch (in.nextLine().trim()) {
            case "a" -> {
                Reader r = new Reader();
                System.out.print("借书证号："); r.cardNo = in.nextLine().trim();
                System.out.print("姓名："); r.name = in.nextLine().trim();
                System.out.print("电话："); r.phone = in.nextLine().trim();
                System.out.println("已新增，ID = " + svc.addReader(r));
            }
            case "p" -> {
                System.out.print("读者 ID："); long id = readInt();
                System.out.print("缴费金额："); double amount = readDouble();
                svc.payFine(id, amount);
                System.out.println("已缴纳");
            }
        }
    }

    // ------------------------------------------------------------ 借还

    private void loanMenu() throws Exception {
        System.out.println("  1 借书  2 还书  3 未归还列表  回车返回");
        System.out.print("操作：");
        switch (in.nextLine().trim()) {
            case "1" -> {
                System.out.print("图书 ID："); long bid = readInt();
                System.out.print("读者 ID："); long rid = readInt();
                LoanResult r = svc.borrow(bid, rid, LocalDate.now());
                System.out.println((r.ok ? "✔ " : "✘ ") + r.message);
            }
            case "2" -> {
                System.out.print("图书 ID："); long bid = readInt();
                System.out.print("读者 ID："); long rid = readInt();
                LoanResult r = svc.giveBack(bid, rid, LocalDate.now());
                System.out.println((r.ok ? "✔ " : "✘ ") + r.message);
            }
            case "3" -> {
                List<Loan> open = svc.openLoans();
                System.out.println();
                Table.print(new String[]{ "借阅ID", "图书", "读者", "应还", "逾期" },
                        open.stream().map(l -> new String[]{
                                String.valueOf(l.id),
                                Table.fit(String.valueOf(l.bookId), 8),
                                Table.fit(String.valueOf(l.readerId), 8),
                                l.dueAt.toString(),
                                l.overdueDays(LocalDate.now()) + " 天"
                        }).toList(),
                        new boolean[]{ true, true, true, false, true });
            }
        }
    }

    // ------------------------------------------------------------ 预约

    private void reserveMenu() throws Exception {
        System.out.print("图书 ID："); long bid = readInt();
        System.out.print("读者 ID："); long rid = readInt();
        LoanResult r = svc.reserve(bid, rid);
        System.out.println((r.ok ? "✔ " : "✘ ") + r.message);
    }

    // ------------------------------------------------------------ 报表

    private void reportMenu() throws Exception {
        System.out.println();
        System.out.println("【分类借阅量】");
        Table.print(new String[]{ "分类", "借阅次数", "品种数" }, report.loansByCategory(),
                new boolean[]{ false, true, true });

        System.out.println();
        System.out.println("【最近半年借阅趋势】");
        Table.print(new String[]{ "月份", "借阅次数" }, report.monthlyTrend(6),
                new boolean[]{ false, true });

        System.out.println();
        System.out.println("【热门图书 TOP 10】");
        Table.print(new String[]{ "书名", "作者", "借阅次数" }, report.topBooks(10),
                new boolean[]{ false, false, true });

        System.out.println();
        System.out.println("【当前逾期名单】");
        Table.print(new String[]{ "读者", "电话", "书名", "应还", "逾期", "罚金" },
                report.overdue(LocalDate.now()), new boolean[]{ false, false, false, false, true, true });

        System.out.print("是否导出热门图书 CSV？(y/N)：");
        if (in.nextLine().trim().equalsIgnoreCase("y"))
            System.out.println("已导出：" + report.exportTopBooksCsv("top_books.csv", 10));
    }

    // ------------------------------------------------------------

    private long readInt() {
        try { return Long.parseLong(in.nextLine().trim()); } catch (Exception e) { return 0; }
    }

    private double readDouble() {
        try { return Double.parseDouble(in.nextLine().trim()); } catch (Exception e) { return 0; }
    }
}
