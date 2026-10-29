package library;

/** 图书 / 读者 / 借阅 / 预约 的实体，字段与数据库表一一对应。 */
public class Models {

    public static class Book {
        public long id;
        public String isbn;
        public String title;
        public String author;
        public String category;
        public int total;
        public int available;

        public int borrowed() { return total - available; }

        @Override public String toString() {
            return String.format("#%d %s《%s》 %s/%s", id, isbn, title, available, total);
        }
    }

    public static class Reader {
        public long id;
        public String cardNo;
        public String name;
        public String phone;
        public int maxLoans = 5;
        public double balance;
        public boolean enabled = true;
    }

    public static class Loan {
        public long id;
        public long bookId;
        public long readerId;
        public java.time.LocalDateTime borrowedAt;
        public java.time.LocalDate dueAt;
        public java.time.LocalDateTime returnedAt;
        public double fine;

        public boolean open() { return returnedAt == null; }

        public long overdueDays(java.time.LocalDate today) {
            return today.isAfter(dueAt) ? java.time.temporal.ChronoUnit.DAYS.between(dueAt, today) : 0;
        }
    }

    public static class Reservation {
        public long id;
        public long bookId;
        public long readerId;
        public java.time.LocalDateTime createdAt;
        public java.time.LocalDateTime notifiedAt;
        public boolean cancelled;
    }

    /** 借书 / 还书结果，供界面与自测断言使用。 */
    public static class LoanResult {
        public final boolean ok;
        public final String message;
        public final double fine;
        public final long loanId;

        private LoanResult(boolean ok, String message, double fine, long loanId) {
            this.ok = ok; this.message = message; this.fine = fine; this.loanId = loanId;
        }

        public static LoanResult fail(String message) { return new LoanResult(false, message, 0, 0); }
        public static LoanResult ok(String message) { return new LoanResult(true, message, 0, 0); }
        public static LoanResult ok(String message, double fine) { return new LoanResult(true, message, fine, 0); }
        public static LoanResult ok(String message, long loanId) { return new LoanResult(true, message, 0, loanId); }
    }
}
