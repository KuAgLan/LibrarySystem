package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.common.BizException;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.ReportMapper;
import com.library.vo.LoanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统计报表 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final LoanMapper loanMapper;
    private final ReportMapper reportMapper;

    public Map<String, Object> dashboard() {
        long titles = bookMapper.selectCount(Wrappers.<Book>lambdaQuery());
        long copies = bookMapper.selectList(Wrappers.<Book>lambdaQuery())
                .stream().mapToLong(b -> b.getTotalCopies() == null ? 0 : b.getTotalCopies()).sum();
        long available = bookMapper.selectList(Wrappers.<Book>lambdaQuery())
                .stream().mapToLong(b -> b.getAvailableCopies() == null ? 0 : b.getAvailableCopies()).sum();
        long readers = readerMapper.selectCount(Wrappers.<Reader>lambdaQuery());
        long suspended = readerMapper.selectCount(Wrappers.<Reader>lambdaQuery().eq(Reader::getStatus, "SUSPENDED"));
        long borrowing = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery().eq(Loan::getStatus, "BORROWED"));
        long overdue = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .eq(Loan::getStatus, "BORROWED").lt(Loan::getDueAt, java.time.LocalDateTime.now()));
        long todayBorrow = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .ge(Loan::getBorrowedAt, LocalDate.now().atStartOfDay()));
        long todayReturn = loanMapper.selectCount(Wrappers.<Loan>lambdaQuery()
                .ge(Loan::getReturnedAt, LocalDate.now().atStartOfDay()));
        BigDecimal unpaid = readerMapper.selectList(Wrappers.<Reader>lambdaQuery())
                .stream().map(r -> r.getUnpaidFine() == null ? BigDecimal.ZERO : r.getUnpaidFine())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fineToday = loanMapper.selectList(Wrappers.<Loan>lambdaQuery()
                        .ge(Loan::getReturnedAt, LocalDate.now().atStartOfDay())
                        .gt(Loan::getFineAmount, BigDecimal.ZERO))
                .stream().map(Loan::getFineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("titleCount", titles);
        stat.put("copyCount", copies);
        stat.put("availableCount", available);
        stat.put("readerCount", readers);
        stat.put("suspendedReaders", suspended);
        stat.put("borrowing", borrowing);
        stat.put("overdue", overdue);
        stat.put("todayBorrow", todayBorrow);
        stat.put("todayReturn", todayReturn);
        stat.put("unpaidFine", unpaid);
        stat.put("fineToday", fineToday);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stat", stat);
        result.put("categories", reportMapper.categoryStats());
        result.put("trend", reportMapper.monthlyTrend());
        result.put("topBooks", reportMapper.topBooks(10));
        result.put("overdueList", loanMapper.selectOverdueList(20));
        return result;
    }

    public List<LoanVO> overdueList(int limit) {
        return loanMapper.selectOverdueList(Math.min(100, Math.max(1, limit)));
    }

    public String exportLoansCsv(String status) {
        List<LoanVO> rows = loanMapper.selectVoPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 5000),
                status == null || status.isBlank() ? "ALL" : status, null, null).getRecords();
        StringBuilder sb = new StringBuilder("\ufeff");
        sb.append("借阅ID,图书,ISBN,读者,借书证,借出时间,应还时间,归还时间,状态,逾期天数,罚金,已缴\n");
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (LoanVO l : rows) {
            long lateDays = l.getReturnedAt() != null
                    ? Math.max(0, java.time.Duration.between(l.getDueAt(), l.getReturnedAt()).toDays())
                    : Math.max(0, java.time.Duration.between(l.getDueAt(), now).toDays());
            sb.append(l.getId()).append(',')
                    .append(csv(l.getBookTitle())).append(',')
                    .append(csv(l.getBookIsbn())).append(',')
                    .append(csv(l.getReaderName())).append(',')
                    .append(csv(l.getReaderCardNo())).append(',')
                    .append(l.getBorrowedAt() == null ? "" : l.getBorrowedAt().toString().replace('T', ' ')).append(',')
                    .append(l.getDueAt() == null ? "" : l.getDueAt().toString().replace('T', ' ')).append(',')
                    .append(l.getReturnedAt() == null ? "" : l.getReturnedAt().toString().replace('T', ' ')).append(',')
                    .append(l.getStatus()).append(',')
                    .append(lateDays).append(',')
                    .append(l.getFineAmount() == null ? "0.00" : l.getFineAmount()).append(',')
                    .append(l.getFinePaid() != null && l.getFinePaid() == 1 ? "是" : "否")
                    .append("\r\n");
        }
        return sb.toString();
    }

    private String csv(String v) {
        return '"' + (v == null ? "" : v.replace("\"", "\"\"")) + '"';
    }
}
