package library;

import library.Models.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 业务规则：借阅上限、库存、逾期罚金、预约队列、欠费封禁。 */
public class LibraryService {

    public static final double FINE_PER_DAY = 0.5;   // 每册每天 0.5 元
    public static final double FINE_BLOCK = 20.0;    // 欠费超过 20 元停止借阅
    public static final int LOAN_DAYS = 30;          // 借期 30 天

    public interface Tx<T> { T run(Connection c) throws SQLException; }

    /** 开事务执行，成功提交、异常回滚。 */
    public static <T> T tx(Tx<T> body) throws SQLException {
        try (Connection c = Db.connect()) {
            c.setAutoCommit(false);
            try {
                T r = body.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------ 图书

    public List<Book> searchBooks(String keyword) throws SQLException {
        try (Connection c = Db.connect()) { return Dao.searchBooks(c, keyword); }
    }

    public Book getBook(long id) throws SQLException {
        try (Connection c = Db.connect()) { return Dao.getBook(c, id); }
    }

    public long addBook(Book b) throws SQLException {
        return tx(c -> {
            long id = Dao.insertBook(c, b);
            Dao.audit(c, "ADD_BOOK", b.isbn + " " + b.title + " x" + b.total);
            return id;
        });
    }

    public void updateBook(Book b) throws SQLException {
        tx(c -> { Dao.updateBook(c, b); Dao.audit(c, "UPDATE_BOOK", b.isbn + " " + b.title); return null; });
    }

    public void removeBook(long id) throws SQLException {
        tx(c -> { Dao.softDeleteBook(c, id); Dao.audit(c, "REMOVE_BOOK", String.valueOf(id)); return null; });
    }

    // ------------------------------------------------------------ 读者

    public List<Reader> readers() throws SQLException {
        try (Connection c = Db.connect()) { return Dao.allReaders(c); }
    }

    public Reader reader(long id) throws SQLException {
        try (Connection c = Db.connect()) { return Dao.getReader(c, id); }
    }

    public long addReader(Reader r) throws SQLException {
        return tx(c -> {
            long id = Dao.insertReader(c, r);
            Dao.audit(c, "ADD_READER", r.cardNo + " " + r.name);
            return id;
        });
    }

    public void payFine(long readerId, double amount) throws SQLException {
        tx(c -> {
            Dao.addBalance(c, readerId, -Math.abs(amount));
            Dao.audit(c, "PAY_FINE", readerId + " -" + amount);
            return null;
        });
    }

    // ------------------------------------------------------------ 借还

    /**
     * 借书：校验 → 条件扣库存 → 落借阅记录，全程一个事务。
     * 并发时靠 UPDATE ... WHERE available > 0 的行锁保证只有一个人抢到最后一册。
     */
    public LoanResult borrow(long bookId, long readerId, LocalDate today) throws SQLException {
        return tx(c -> {
            Reader reader = Dao.getReader(c, readerId);
            if (reader == null) return LoanResult.fail("读者不存在");
            if (!reader.enabled) return LoanResult.fail("该读者卡已停用");

            Book book = Dao.getBook(c, bookId);
            if (book == null) return LoanResult.fail("图书不存在");

            for (Loan l : Dao.openLoans(c)) {
                if (l.readerId == readerId && l.overdueDays(today) > 0)
                    return LoanResult.fail("有书已逾期未还，请先归还");
            }

            if (reader.balance > FINE_BLOCK)
                return LoanResult.fail(String.format("欠费 %.2f 元，超过 %.0f 元上限", reader.balance, FINE_BLOCK));

            int borrowed = Dao.openLoanCount(c, readerId);
            if (borrowed >= reader.maxLoans)
                return LoanResult.fail("已达借阅上限（" + reader.maxLoans + " 册）");

            if (Dao.getOpenLoan(c, bookId, readerId) != null)
                return LoanResult.fail("这本书你已经借了");

            if (Dao.tryTakeCopy(c, bookId) == 0)
                return LoanResult.fail("该书暂无可借副本");

            LocalDate due = today.plusDays(LOAN_DAYS);
            long loanId = Dao.insertLoan(c, bookId, readerId, LocalDateTime.now(), due);
            Dao.audit(c, "BORROW", "book=" + bookId + " reader=" + readerId + " due=" + due);
            return LoanResult.ok("借出成功，应还日期 " + due, loanId);
        });
    }

    /** 还书：算逾期罚金 → 关闭借阅 → 回补库存 → 通知预约队列第一位。 */
    public LoanResult giveBack(long bookId, long readerId, LocalDate today) throws SQLException {
        return tx(c -> {
            Loan loan = Dao.getOpenLoan(c, bookId, readerId);
            if (loan == null) return LoanResult.fail("没有找到这条未归还的借阅记录");

            long overdue = loan.overdueDays(today);
            double fine = Math.round(overdue * FINE_PER_DAY * 100) / 100.0;

            Dao.closeLoan(c, loan.id, LocalDateTime.now(), fine);
            Dao.returnCopy(c, bookId);
            if (fine > 0) Dao.addBalance(c, readerId, fine);

            long[] next = Dao.nextWaiting(c, bookId);
            if (next != null) Dao.markNotified(c, next[0]);

            Dao.audit(c, "RETURN", "book=" + bookId + " reader=" + readerId + " overdue=" + overdue + "d fine=" + fine);

            String msg = fine > 0 ? String.format("逾期 %d 天，罚金 %.2f 元", overdue, fine) : "按期归还";
            if (next != null) msg += "；已通知预约队列中的下一位读者";
            return LoanResult.ok(msg, fine);
        });
    }

    // ------------------------------------------------------------ 预约

    public LoanResult reserve(long bookId, long readerId) throws SQLException {
        return tx(c -> {
            Book book = Dao.getBook(c, bookId);
            if (book == null) return LoanResult.fail("图书不存在");
            if (book.available > 0) return LoanResult.fail("该书还有可借副本，直接借阅即可");
            if (Dao.reservationExists(c, bookId, readerId)) return LoanResult.fail("你已经预约过这本书");

            Dao.insertReservation(c, bookId, readerId);
            Dao.audit(c, "RESERVE", "book=" + bookId + " reader=" + readerId);
            return LoanResult.ok("预约成功，有副本归还时会按顺序通知");
        });
    }

    // ------------------------------------------------------------ 其它

    public List<Loan> openLoans() throws SQLException {
        try (Connection c = Db.connect()) { return Dao.openLoans(c); }
    }

    public List<String> recentAudit(int limit) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Connection c = Db.connect();
             var ps = c.prepareStatement("SELECT at,action,detail FROM audit_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(rs.getTimestamp(1).toLocalDateTime() + "  " + rs.getString(2) + "  " + rs.getString(3));
            }
        }
        return list;
    }
}
