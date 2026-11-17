package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.Loan;
import com.library.vo.LoanVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface LoanMapper extends BaseMapper<Loan> {

    @Select("""
            <script>
            SELECT l.*, b.title AS bookTitle, b.isbn AS bookIsbn, b.category AS bookCategory,
                   r.name AS readerName, r.card_no AS readerCardNo, r.type AS readerType,
                   CASE WHEN l.status = 'BORROWED' AND l.due_at &lt; NOW() THEN 1 ELSE 0 END AS overdue
            FROM loan l
            LEFT JOIN book b ON b.id = l.book_id
            LEFT JOIN reader r ON r.id = l.reader_id
            WHERE 1 = 1
            <if test="status != null and status != '' and status != 'ALL'">
              <choose>
                <when test="status == 'OVERDUE'"> AND l.status = 'BORROWED' AND l.due_at &lt; NOW() </when>
                <otherwise> AND l.status = #{status} </otherwise>
              </choose>
            </if>
            <if test="keyword != null and keyword != ''">
              AND (b.title LIKE CONCAT('%', #{keyword}, '%')
                   OR r.name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.card_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="readerId != null"> AND l.reader_id = #{readerId} </if>
            ORDER BY l.id DESC
            </script>
            """)
    com.baomidou.mybatisplus.core.metadata.IPage<LoanVO> selectVoPage(
            com.baomidou.mybatisplus.core.metadata.IPage<LoanVO> page,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("readerId") Long readerId);

    @Select("""
            SELECT COUNT(*) FROM loan
            WHERE reader_id = #{readerId} AND status = 'BORROWED' AND due_at < NOW()
            """)
    long countOverdueByReader(@Param("readerId") Long readerId);

    @Select("""
            SELECT l.*, b.title AS bookTitle, b.isbn AS bookIsbn, r.name AS readerName, r.card_no AS readerCardNo
            FROM loan l LEFT JOIN book b ON b.id = l.book_id LEFT JOIN reader r ON r.id = l.reader_id
            WHERE l.status = 'BORROWED' AND l.due_at < NOW()
            ORDER BY l.due_at ASC LIMIT #{limit}
            """)
    List<LoanVO> selectOverdueList(@Param("limit") int limit);
}
