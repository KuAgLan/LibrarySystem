package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.entity.Reservation;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日结算任务：
 * 1) 统计逾期未还；
 * 2) 逾期超 30 天或欠费达阈值的读者自动停用；
 * 3) 「待取书」超 3 天未取的预约自动取消；
 * 4) 写一条审计汇总。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NightlySettleService {

    private final LoanMapper loanMapper;
    private final ReaderMapper readerMapper;
    private final ReservationMapper reservationMapper;
    private final SettingService settingService;
    private final AuditService auditService;

    @Value("${app.lock.provider:mysql}")
    private String lockProvider;

    @Transactional
    public Map<String, Object> settle() {
        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        List<Loan> overdue = loanMapper.selectList(Wrappers.<Loan>lambdaQuery()
                .eq(Loan::getStatus, "BORROWED").lt(Loan::getDueAt, now));

        BigDecimal blockFine = settingService.getDecimal(SettingService.BLOCK_FINE, "10.00");
        int suspended = 0;
        for (Loan loan : overdue) {
            long days = java.time.Duration.between(loan.getDueAt(), now).toDays();
            Reader reader = readerMapper.selectById(loan.getReaderId());
            if (reader == null || "SUSPENDED".equals(reader.getStatus())) {
                continue;
            }
            boolean fineTooHigh = reader.getUnpaidFine() != null && reader.getUnpaidFine().compareTo(blockFine) >= 0;
            if (days >= 30 || fineTooHigh) {
                Reader update = new Reader();
                update.setId(reader.getId());
                update.setStatus("SUSPENDED");
                readerMapper.updateById(update);
                suspended++;
            }
        }

        List<Reservation> stale = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, "NOTIFIED")
                .lt(Reservation::getNotifiedAt, now.minusDays(3)));
        int cancelled = 0;
        for (Reservation reservation : stale) {
            Reservation update = new Reservation();
            update.setId(reservation.getId());
            update.setStatus("CANCELLED");
            reservationMapper.updateById(update);
            cancelled++;
        }

        long cost = System.currentTimeMillis() - start;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executedAt", now);
        summary.put("overdueLoans", overdue.size());
        summary.put("suspendedReaders", suspended);
        summary.put("expiredReservations", cancelled);
        summary.put("lockProvider", lockProvider);
        summary.put("costMs", cost);

        auditService.log("JOB_NIGHTLY_SETTLE", "job", null,
                "逾期 " + overdue.size() + " 条，停用读者 " + suspended + " 位，取消预约 " + cancelled + " 条，耗时 " + cost + "ms");
        log.info("夜间结算完成：{}", summary);
        return summary;
    }
}