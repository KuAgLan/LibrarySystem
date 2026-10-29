package library;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 统计报表：分类借阅量、月度趋势、热门图书、逾期名单、导出 CSV。 */
public class ReportService {

    public List<String[]> loansByCategory() throws SQLException {
        String sql = """
            SELECT b.category, COUNT(l.id) AS loans, COUNT(DISTINCT b.id) AS titles
            FROM books b LEFT JOIN loans l ON l.book_id = b.id
            WHERE b.deleted = 0
            GROUP BY b.category
            ORDER BY loans DESC, b.category
        """;
        List<String[]> rows = new ArrayList<>();
        try (Connection c = Db.connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) rows.add(new String[]{ rs.getString(1), String.valueOf(rs.getInt(2)), String.valueOf(rs.getInt(3)) });
        }
        return rows;
    }

    public List<String[]> monthlyTrend(int months) throws SQLException {
        String sql = """
            SELECT DATE_FORMAT(borrowed_at, '%Y-%m') AS m, COUNT(*) AS c
            FROM loans GROUP BY m ORDER BY m DESC LIMIT ?
        """;
        List<String[]> rows = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, months);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{ rs.getString(1), String.valueOf(rs.getInt(2)) });
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    public List<String[]> topBooks(int n) throws SQLException {
        String sql = """
            SELECT b.title, b.author, COUNT(l.id) AS c
            FROM books b JOIN loans l ON l.book_id = b.id
            GROUP BY b.id, b.title, b.author
            ORDER BY c DESC, b.title LIMIT ?
        """;
        List<String[]> rows = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, n);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{ rs.getString(1), rs.getString(2), String.valueOf(rs.getInt(3)) });
            }
        }
        return rows;
    }

    /** 逾期名单：未归还且应还日早于今天的借阅。 */
    public List<String[]> overdue(LocalDate today) throws SQLException {
        String sql = """
            SELECT r.name, COALESCE(r.phone,''), b.title, l.due_at
            FROM loans l
            JOIN books b   ON b.id = l.book_id
            JOIN readers r ON r.id = l.reader_id
            WHERE l.returned_at IS NULL AND l.due_at < ?
            ORDER BY l.due_at
        """;
        List<String[]> rows = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate due = rs.getDate(4).toLocalDate();
                    long days = java.time.temporal.ChronoUnit.DAYS.between(due, today);
                    double fine = Math.round(days * LibraryService.FINE_PER_DAY * 100) / 100.0;
                    rows.add(new String[]{ rs.getString(1), rs.getString(2), rs.getString(3),
                            due.toString(), days + " 天", String.format("%.2f", fine) });
                }
            }
        }
        return rows;
    }

    /** 导出热门图书 CSV（UTF-8 BOM，Excel 打开不乱码）。 */
    public String exportTopBooksCsv(String path, int n) throws Exception {
        List<String[]> rows = topBooks(n);
        try (var w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write("排名,书名,作者,借阅次数\n");
            int i = 1;
            for (String[] r : rows) {
                w.write(i++ + "," + esc(r[0]) + "," + esc(r[1]) + "," + r[2] + "\n");
            }
        }
        return path;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return (s.contains(",") || s.contains("\"")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
