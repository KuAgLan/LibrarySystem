package com.library.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BizException;
import com.library.common.PageVO;
import com.library.config.AuthUser;
import com.library.dto.BorrowRequest;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.entity.Reservation;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.ReservationMapper;
import com.library.vo.LoanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 借阅：借书 / 还书 / 续借（含额度、逾期、罚金、预约联动） */
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanMapper loanMapper;
    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final ReservationMapper reservationMapper;
    private final SettingService settingService;
    private final AuditService auditService;

    public PageVO<LoanVO> page(String status, String keyword, Long readerId, long pageNo, long pageSize) {
        IPage<LoanVO> result = loanMapper.selectVoPage(
                new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))),
                status, keyword == null || keyword.isBlank() ? null : keyword.trim(), readerId);
        return PageVO.of(result);
    }

    public List<LoanVO> overdue(int limit) {
        return loanMapper.selectOverdueList(Math.min(50, Math.max(1, limit)));
    }

    /** 借书 */
    @Transactional
    public Loan borrow(BorrowRequest request) {
        Reader reader = readerMapper.selectById(request.readerId());
        if (reader == null) {
            throw new BizException(404, "读者不存在");
        }
        if (!"ACTIVE".equals(reader.getStatus())) {
            throw new BizException(403, "读者已被停用，无法借书");
        }
        Book book = bookMapper.selectById(request.bookId());
        if (book == null) {
            throw new BizException(404, "图书不存在");
        }
        if (!"ACTIVE".equals(book.getStatus())) {
            throw new BizException(400, "该图书已下架");
        }

        int maxBorrow = settingService.maxBorrow(reader.getType());
        if (reader.getBorrowedNow() != null && reader.getBorrowedNow() >= maxBorrow) {
            throw new BizException(400, "已达借阅上限（" + maxBorrow + " 本），请先归还");
        }
        long overdue = loanMapper.countOverdueByReader(reader.getId());
        if (overdue > 0) {
            throw new BizException(400, "有 " + overdue + " 本图书逾期未还，请先归还");
        }
        BigDecimal blockFine = settingService.getDecimal(SettingService.BLOCK_FINE, "10.00");
        BigDecimal unpaid = reader.getUnpaidFine() == null ? BigDecimal.ZERO : reader.getUnpaidFine();
        if (unpaid.compareTo(blockFine) >= 0) {
            throw new BizException(400, "未缴罚金 " + unpaid.toPlainString() + " 元已达上限（" + blockFine.toPlainString() + " 元），请先缴纳");
        }

        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            throw new BizException(400, "暂无可借副本，可提交预约排队");
        }
        // 关键：条件更新扣减库存，避免并发超借
        if (bookMapper.decreaseAvailable(book.getId()) == 0) {
            throw new BizException(409, "该书刚被借走，请刷新后重试");
        }

        readerMapper.adjustBorrowed(reader.getId(), 1, 1);

        LocalDateTime now = LocalDateTime.now();
        Loan loan = new Loan();
        loan.setBookId(book.getId());
        loan.setReaderId(reader.getId());
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plusDays(settingService.loanDays(reader.getType())));
        loan.setRenewCount(0);
        loan.setStatus("BORROWED");
        loan.setFineAmount(BigDecimal.ZERO);
        loan.setFinePaid(0);
        loan.setRemark(request.remark() == null ? "" : request.remark());
        AuthUser user = auditService.current();
        loan.setOperator(user == null ? "system" : user.name());
        loanMapper.insert(loan);

        auditService.log("LOAN_BORROW", "loan", loan.getId(),
                "借出《" + book.getTitle() + "》给 " + reader.getName() + "，应还 " + loan.getDueAt().toLocalDate());
        return loan;
    }

    /** 还书：结算逾期罚金、归还库存、联动预约 */
    @Transactional
    public Loan returnBook(Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null) {
            throw new BizException(404, "借阅记录不存在");
        }
        if (!"BORROWED".equals(loan.getStatus())) {
            throw new BizException(400, "该记录已归还或已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        long lateDays = Math.max(0, (long) Math.ceil(Duration.between(loan.getDueAt(), now).toHours() / 24.0));
        BigDecimal rate = settingService.getDecimal(SettingService.FINE_PER_DAY, "0.50");
        BigDecimal maxFine = settingService.getDecimal(SettingService.MAX_FINE, "50.00");
        BigDecimal fine = rate.multiply(BigDecimal.valueOf(lateDays)).setScale(2, RoundingMode.HALF_UP);
        if (fine.compareTo(maxFine) > 0) {
            fine = maxFine;
        }

        Loan update = new Loan();
        update.setId(loanId);
        update.setReturnedAt(now);
        update.setStatus("RETURNED");
        update.setFineAmount(fine);
        update.setFinePaid(fine.compareTo(BigDecimal.ZERO) == 0 ? 1 : 0);
        update.setRemark((loan.getRemark() == null ? "" : loan.getRemark())
                + (lateDays > 0 ? " | 逾期 " + lateDays + " 天" : ""));
        loanMapper.updateById(update);

        bookMapper.increaseAvailable(loan.getBookId());
        readerMapper.adjustBorrowed(loan.getReaderId(), -1, 0);
        if (fine.compareTo(BigDecimal.ZERO) > 0) {
            readerMapper.addFine(loan.getReaderId(), fine);
            Reader reader = readerMapper.selectById(loan.getReaderId());
            BigDecimal blockFine = settingService.getDecimal(SettingService.BLOCK_FINE, "10.00");
            if (reader != null && reader.getUnpaidFine() != null && reader.getUnpaidFine().compareTo(blockFine) >= 0) {
                Reader suspend = new Reader();
                suspend.setId(reader.getId());
                suspend.setStatus("SUSPENDED");
                readerMapper.updateById(suspend);
            }
        }

        notifyFirstReservation(loan.getBookId());
        auditService.log("LOAN_RETURN", "loan", loanId,
                "归还图书，逾期 " + lateDays + " 天，罚金 " + fine.toPlainString() + " 元");
        return loanMapper.selectById(loanId);
    }

    /** 续借 */
    @Transactional
    public Loan renew(Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null) {
            throw new BizException(404, "借阅记录不存在");
        }
        if (!"BORROWED".equals(loan.getStatus())) {
            throw new BizException(400, "只有未归还的记录可以续借");
        }
        if (loan.getDueAt().isBefore(LocalDateTime.now())) {
            throw new BizException(400, "已逾期，不能续借，请先归还");
        }
        int limit = settingService.getInt(SettingService.RENEW_LIMIT, 1);
        if (loan.getRenewCount() != null && loan.getRenewCount() >= limit) {
            throw new BizException(400, "续借次数已达上限（" + limit + " 次）");
        }
        long waiting = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getBookId, loan.getBookId()).eq(Reservation::getStatus, "WAITING"));
        if (waiting > 0) {
            throw new BizException(400, "该图书有 " + waiting + " 人预约，不能续借");
        }
        int days = settingService.getInt(SettingService.RENEW_DAYS, 15);
        Loan update = new Loan();
        update.setId(loanId);
        update.setDueAt(loan.getDueAt().plusDays(days));
        update.setRenewCount((loan.getRenewCount() == null ? 0 : loan.getRenewCount()) + 1);
        loanMapper.updateById(update);
        auditService.log("LOAN_RENEW", "loan", loanId, "续借 " + days + " 天，新应还日期 " + update.getDueAt().toLocalDate());
        return loanMapper.selectById(loanId);
    }

    private void notifyFirstReservation(Long bookId) {
        Reservation first = reservationMapper.selectOne(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getBookId, bookId).eq(Reservation::getStatus, "WAITING")
                .orderByAsc(Reservation::getId).last("limit 1"));
        if (first != null) {
            Reservation update = new Reservation();
            update.setId(first.getId());
            update.setStatus("NOTIFIED");
            update.setNotifiedAt(LocalDateTime.now());
            reservationMapper.updateById(update);
            auditService.log("RESERVATION_NOTIFY", "reservation", first.getId(), "归还后通知预约读者取书");
        }
    }
}
