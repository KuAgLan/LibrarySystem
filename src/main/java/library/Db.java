package library;

import java.sql.*;

/** 连接管理 + 建库建表 + 事务辅助。 */
public class Db {

    private static String url = "jdbc:mysql://127.0.0.1:3306/library"
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
            + "&serverTimezone=Asia/Shanghai";
    private static String user = "library";
    private static String password = "library123";

    public static void configure(String jdbcUrl, String dbUser, String dbPassword) {
        if (jdbcUrl != null) url = jdbcUrl;
        if (dbUser != null) user = dbUser;
        if (dbPassword != null) password = dbPassword;
    }

    public static String url() { return url; }

    /** 数据库名（jdbc:mysql://host:port/db?... 里最后那段）。 */
    public static String databaseName() {
        String path = url.substring(url.indexOf("//") + 2);
        int slash = path.indexOf('/');
        if (slash < 0) return "library";
        path = path.substring(slash + 1);
        int q = path.indexOf('?');
        return q > 0 ? path.substring(0, q) : path;
    }

    /** 不带库名的连接，用于 CREATE DATABASE。 */
    public static Connection connectServer() throws SQLException {
        String base = url.substring(0, url.indexOf("//") + 2)
                + url.substring(url.indexOf("//") + 2).replaceFirst("/[^/?]*(\\?|$)", "/$1");
        return DriverManager.getConnection(base, user, password);
    }

    public static Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(true);
        return conn;
    }

    /** 建库（若不存在）+ 建表 + 建索引，可重复执行。 */
    public static void initSchema() throws SQLException {
        try (Connection server = connectServer(); Statement st = server.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + databaseName()
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            for (String ddl : Schema.DDL) st.executeUpdate(ddl);
        }
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c != null) try { c.close(); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ 参数工具

    public static void set(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v == null) ps.setNull(i + 1, Types.VARCHAR);
            else if (v instanceof String s) ps.setString(i + 1, s);
            else if (v instanceof Integer n) ps.setInt(i + 1, n);
            else if (v instanceof Long n) ps.setLong(i + 1, n);
            else if (v instanceof Double d) ps.setDouble(i + 1, d);
            else if (v instanceof Timestamp t) ps.setTimestamp(i + 1, t);
            else ps.setObject(i + 1, v);
        }
    }

    public static Timestamp ts(java.time.LocalDateTime t) { return Timestamp.valueOf(t); }

    public static java.time.LocalDateTime ts2(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
