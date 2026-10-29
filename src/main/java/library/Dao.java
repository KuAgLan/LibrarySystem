package library;

import library.Models.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 数据访问层：纯 SQL，不含业务规则。 */
public class Dao {

    // ------------------------------------------------------------ 图书

    public static List<Book> searchBooks(Connection c, String keyword) throws SQLException {
        String sql = """
            SELECT id, isbn, title, author, category, total, available
            FROM books
            WHERE deleted = 0
              AND (? IS NULL OR title LIKE ? OR author LIKE ? OR isbn LIKE ?)
            ORDER BY category, title
        """;
        String like = keyword == null ? null : "%" + keyword + "%";
        List<Book> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Db.set(ps, keyword, like, like, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBook(rs));
            }
        }
        return list;
    }

    public static Book getBook(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, isbn, title, author, category, total, available FROM books WHERE id=? AND deleted=0")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapBook(rs) : null;
            }
        }
    }

    private static Book mapBook(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.id = rs.getLong("id");
        b.isbn = rs.getString("isbn");
        b.title = rs.getString("title");
        b.author = rs.getString("author");
        b.category = rs.getString("category");
        b.total = rs.getInt("total");
        b.available = rs.getInt("available");
        return b;
    }

    public static long insertBook(Connection c, Book b) throws SQLException {
        String sql = "INSERT INTO books(isbn,title,author,category,total,available) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Db.set(ps, b.isbn, b.title, b.author, b.category, b.total, b.available);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public static void updateBook(Connection c, Book b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET isbn=?,title=?,author=?,category=?,total=?,available=? WHERE id=?")) {
            Db.set(ps, b.isbn, b.title, b.author, b.category, b.total, b.available, b.id);
            ps.executeUpdate();
        }
    }

    public static void softDeleteBook(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE books SET deleted=1 WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** 条件扣减：还有库存才扣得动，返回 0 表示被别人抢走了。 */
    public static int tryTakeCopy(Connection c, long bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET available = available - 1 WHERE id=? AND available > 0")) {
            ps.setLong(1, bookId);
            return ps.executeUpdate();
        }
    }

    public static void returnCopy(Connection c, long bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET available = LEAST(available + 1, total) WHERE id=?")) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ 读者

    public static List<Reader> allReaders(Connection c) throws SQLException {
        List<Reader> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,card_no,name,phone,max_loans,balance,enabled FROM readers ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapReader(rs));
        }
        return list;
    }

    public static Reader getReader(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,card_no,name,phone,max_loans,balance,enabled FROM readers WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapReader(rs) : null;
            }
        }
    }

    private static Reader mapReader(ResultSet rs) throws SQLException {
        Reader r = new Reader();
        r.id = rs.getLong("id");
        r.cardNo = rs.getString("card_no");
        r.name = rs.getString("name");
        r.phone = rs.getString("phone");
        r.maxLoans = rs.getInt("max_loans");
        r.balance = rs.getDouble("balance");
        r.enabled = rs.getInt("enabled") == 1;
        return r;
    }

    public static long insertReader(Connection c, Reader r) throws SQLException {
        String sql = "INSERT INTO readers(card_no,name,phone,max_loans,balance,enabled,created_at) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Db.set(ps, r.cardNo, r.name, r.phone == null ? "" : r.phone, r.maxLoans,
                    r.balance, r.enabled ? 1 : 0, Db.ts(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public static void addBalance(Connection c, long readerId, double delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE readers SET balance = ROUND(balance + ?, 2) WHERE id=?")) {
            Db.set(ps, delta, readerId);
            ps.executeUpdate();
        }
    }

    public static int openLoanCount(Connection c, long readerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE reader_id=? AND returned_at IS NULL")) {
            ps.setLong(1, readerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------ 借阅

    public static long insertLoan(Connection c, long bookId, long readerId, LocalDateTime borrowedAt, java.time.LocalDate dueAt)
            throws SQLException {
        String sql = "INSERT INTO loans(book_id,reader_id,borrowed_at,due_at) VALUES(?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Db.set(ps, bookId, readerId, Db.ts(borrowedAt), java.sql.Date.valueOf(dueAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public static Loan getOpenLoan(Connection c, long bookId, long readerId) throws SQLException {
        String sql = """
            SELECT id,book_id,reader_id,borrowed_at,due_at,returned_at,fine
            FROM loans WHERE book_id=? AND reader_id=? AND returned_at IS NULL
            ORDER BY id LIMIT 1
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Db.set(ps, bookId, readerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapLoan(rs) : null;
            }
        }
    }

    private static Loan mapLoan(ResultSet rs) throws SQLException {
        Loan l = new Loan();
        l.id = rs.getLong("id");
        l.bookId = rs.getLong("book_id");
        l.readerId = rs.getLong("reader_id");
        l.borrowedAt = Db.ts2(rs.getTimestamp("borrowed_at"));
        l.dueAt = rs.getDate("due_at").toLocalDate();
        l.returnedAt = Db.ts2(rs.getTimestamp("returned_at"));
        l.fine = rs.getDouble("fine");
        return l;
    }

    public static void closeLoan(Connection c, long loanId, LocalDateTime returnedAt, double fine) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loans SET returned_at=?, fine=? WHERE id=? AND returned_at IS NULL")) {
            Db.set(ps, Db.ts(returnedAt), fine, loanId);
            ps.executeUpdate();
        }
    }

    public static List<Loan> openLoans(Connection c) throws SQLException {
        List<Loan> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,book_id,reader_id,borrowed_at,due_at,returned_at,fine FROM loans " +
                "WHERE returned_at IS NULL ORDER BY due_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapLoan(rs));
        }
        return list;
    }

    // ------------------------------------------------------------ 预约

    public static long insertReservation(Connection c, long bookId, long readerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reservations(book_id,reader_id,created_at) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            Db.set(ps, bookId, readerId, Db.ts(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public static boolean reservationExists(Connection c, long bookId, long readerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM reservations WHERE book_id=? AND reader_id=? AND cancelled=0")) {
            Db.set(ps, bookId, readerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static long[] nextWaiting(Connection c, long bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,reader_id FROM reservations WHERE book_id=? AND cancelled=0 AND notified_at IS NULL " +
                "ORDER BY created_at, id LIMIT 1")) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new long[]{ rs.getLong(1), rs.getLong(2) } : null;
            }
        }
    }

    public static void markNotified(Connection c, long reservationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE reservations SET notified_at=? WHERE id=?")) {
            Db.set(ps, Db.ts(LocalDateTime.now()), reservationId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ 审计

    public static void audit(Connection c, String action, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO audit_log(at,action,detail) VALUES(?,?,?)")) {
            Db.set(ps, Db.ts(LocalDateTime.now()), action, detail);
            ps.executeUpdate();
        }
    }
}
