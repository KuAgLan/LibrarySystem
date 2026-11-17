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
import com.library.vo.ReaderBookVO;
import com.library.vo.ReaderProfileVO;
import com.library.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 读者门户：只能操作「自己」的借阅 / 预约 / 资料，管理员接口不可用。
 */
@Service
@RequiredArgsConstructor
public class ReaderPortalService {

    private final ReaderMapper readerMapper;
    private final BookMapper bookMapper;
    private final LoanMapper loanMapper;
    private final ReservationMapper reservationMapper;
    private final LoanService loanService;
    private final SettingService settingService;
    private final AuditService auditService;

    private Reader requireReader(AuthUser user) {
        if (user == null || !user.isReader()) {
            throw new BizException(403, "该功能仅读者可用");
        }
        Reader reader = readerMapper.selectById(user.id());
        if (reader == null) {
            throw new BizException(404, "读者不存在");
        }
        return reader;
    }

    public ReaderProfileVO profile(AuthUser user) {
        Reader reader = requireReader(user);
        List<LoanVO> current = loanMapper.selectVoPage(new Page<>(1, 20), "BORROWED", null, reader.getId()).getRecords();
        long overdue = loanMapper.countOverdueByReader(reader.getId());
        long waiting = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getReaderId, reader.getId()).eq(Reservation::getStatus, "WAITING"));
        return new ReaderProfileVO(reader.getId(), reader.getCardNo(), reader.getName(), reader.getPhone(),
                reader.getEmail(), reader.getType(), reader.getDepartment(), reader.getStatus(),
                reader.getUnpaidFine() == null ? BigDecimal.ZERO : reader.getUnpaidFine(),
                reader.getBorrowedNow(), reader.getTotalBorrow(),
                settingService.maxBorrow(reader.getType()), settingService.loanDays(reader.getType()),
                current.size(), overdue, waiting, current, reader.getCreatedAt());
    }

    /** 馆藏检索（读者视角，只返回公开字段） */
    public PageVO<ReaderBookVO> search(String keyword, String category, long pageNo, long pageSize, boolean onlyAvailable) {
        var wrapper = Wrappers.<Book>lambdaQuery().eq(Book::getStatus, "ACTIVE");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Book::getTitle, kw).or().like(Book::getAuthor, kw).or().like(Book::getIsbn, kw));
        }
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            wrapper.eq(Book::getCategory, category);
        }
        if (onlyAvailable) {
            wrapper.gt(Book::getAvailableCopies, 0);
        }
        wrapper.orderByDesc(Book::getAvailableCopies).orderByDesc(Book::getId);
        IPage<Book> result = bookMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, pageSize))), wrapper);
        List<ReaderBookVO> items = result.getRecords().stream()
                .map(b -> new ReaderBookVO(b.getId(), b.getIsbn(), b.getTitle(), b.getAuthor(), b.getPublisher(),
                        b.getCategory(), b.getLocation(), b.getAvailableCopies(), b.getTotalCopies(),
                        b.getPublishYear(), b.getPrice()))
                .toList();
        long size = Math.max(1, result.getSize());
        return new PageVO<>(items, result.getTotal(), result.getCurrent(), result.getSize(),
                Math.max(1, (long) Math.ceil(result.getTotal() / (double) size)));
    }

    public PageVO<LoanVO> myLoans(AuthUser user, String status, long pageNo, long pageSize) {
        Reader reader = requireReader(user);
        return PageVO.of(loanMapper.selectVoPage(new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, pageSize))),
                status, null, reader.getId()));
    }

    /** 读者自助借书：与管理员借书同一套校验，但读者 ID 由令牌决定 */
    @Transactional
    public Loan borrow(AuthUser user, Long bookId, String remark) {
        Reader reader = requireReader(user);
        return loanService.borrow(new BorrowRequest(bookId, reader.getId(),
                remark == null || remark.isBlank() ? "读者自助借阅" : remark));
    }

    @Transactional
    public Loan renew(AuthUser user, Long loanId) {
        Reader reader = requireReader(user);
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null || !reader.getId().equals(loan.getReaderId())) {
            throw new BizException(404, "借阅记录不存在");
        }
        return loanService.renew(loanId);
    }

    @Transactional
    public Reservation reserve(AuthUser user, Long bookId) {
        Reader reader = requireReader(user);
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException(404, "图书不存在");
        }
        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            throw new BizException(400, "该图书当前有可借副本，可直接借阅");
        }
        long exists = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getBookId, bookId)
                .eq(Reservation::getReaderId, reader.getId())
                .in(Reservation::getStatus, List.of("WAITING", "NOTIFIED")));
        if (exists > 0) {
            throw new BizException(400, "你已预约该书，请耐心等待通知");
        }
        Reservation reservation = new Reservation();
        reservation.setBookId(bookId);
        reservation.setReaderId(reader.getId());
        reservation.setStatus("WAITING");
        reservationMapper.insert(reservation);
        auditService.log("READER_RESERVE", "reservation", reservation.getId(),
                reader.getName() + " 自助预约《" + book.getTitle() + "》");
        return reservation;
    }

    public List<ReservationVO> myReservations(AuthUser user) {
        Reader reader = requireReader(user);
        return reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getReaderId, reader.getId())
                        .orderByDesc(Reservation::getId))
                .stream().map(r -> {
                    Book book = bookMapper.selectById(r.getBookId());
                    return new ReservationVO(r.getId(), r.getBookId(), book == null ? "—" : book.getTitle(),
                            r.getReaderId(), reader.getName(), reader.getCardNo(),
                            r.getStatus(), r.getCreatedAt(), r.getNotifiedAt());
                }).toList();
    }

    @Transactional
    public void cancelReservation(AuthUser user, Long id) {
        Reader reader = requireReader(user);
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null || !reader.getId().equals(reservation.getReaderId())) {
            throw new BizException(404, "预约不存在");
        }
        if (!"WAITING".equals(reservation.getStatus()) && !"NOTIFIED".equals(reservation.getStatus())) {
            throw new BizException(400, "该预约已结束");
        }
        Reservation update = new Reservation();
        update.setId(id);
        update.setStatus("CANCELLED");
        reservationMapper.updateById(update);
        auditService.log("READER_RESERVE_CANCEL", "reservation", id, "读者取消预约");
    }

    /** 读者不可自助缴费，仅展示金额与缴费指引 */
    public BigDecimal myFine(AuthUser user) {
        Reader reader = requireReader(user);
        return reader.getUnpaidFine() == null ? BigDecimal.ZERO : reader.getUnpaidFine();
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}