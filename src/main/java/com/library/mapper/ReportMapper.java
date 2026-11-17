package com.library.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 报表聚合查询 */
@Mapper
public interface ReportMapper {

    @Select("""
            SELECT b.category AS category, COUNT(*) AS loans, COUNT(DISTINCT l.book_id) AS titles
            FROM loan l JOIN book b ON b.id = l.book_id
            GROUP BY b.category ORDER BY loans DESC
            """)
    List<Map<String, Object>> categoryStats();

    @Select("""
            SELECT DATE_FORMAT(l.borrowed_at, '%Y-%m') AS month, COUNT(*) AS loans
            FROM loan l WHERE l.borrowed_at >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH)
            GROUP BY month ORDER BY month ASC
            """)
    List<Map<String, Object>> monthlyTrend();

    @Select("""
            SELECT b.id, b.title, b.author, b.category, COUNT(l.id) AS loans
            FROM loan l JOIN book b ON b.id = l.book_id
            GROUP BY b.id, b.title, b.author, b.category
            ORDER BY loans DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> topBooks(int limit);
}
