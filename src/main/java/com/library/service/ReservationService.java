package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.common.BizException;
import com.library.dto.ReservationRequest;
import com.library.entity.Book;
import com.library.entity.Reader;
import com.library.entity.Reservation;
import com.library.mapper.BookMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.ReservationMapper;
import com.library.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 预约排队：无可借副本时排队，归还时通知队首 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final AuditService auditService;

    public List<ReservationVO> list(String status) {
        String filter = status == null || status.isBlank() ? "WAITING" : status.toUpperCase();
        return reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                        .eq(!"ALL".equals(filter), Reservation::getStatus, filter)
                        .orderByAsc(Reservation::getId))
                .stream().map(r -> {
                    Book book = bookMapper.selectById(r.getBookId());
                    Reader reader = readerMapper.selectById(r.getReaderId());
                    return new ReservationVO(r.getId(), r.getBookId(),
                            book == null ? "—" : book.getTitle(),
                            r.getReaderId(), reader == null ? "—" : reader.getName(),
                            reader == null ? "—" : reader.getCardNo(),
                            r.getStatus(), r.getCreatedAt(), r.getNotifiedAt());
                }).toList();
    }

    @Transactional
    public Reservation create(ReservationRequest request) {
        Book book = bookMapper.selectById(request.bookId());
        if (book == null) {
            throw new BizException(404, "图书不存在");
        }
        Reader reader = readerMapper.selectById(request.readerId());
        if (reader == null) {
            throw new BizException(404, "读者不存在");
        }
        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            throw new BizException(400, "该图书当前有可借副本，无需预约");
        }
        long exists = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getBookId, book.getId())
                .eq(Reservation::getReaderId, reader.getId())
                .in(Reservation::getStatus, List.of("WAITING", "NOTIFIED")));
        if (exists > 0) {
            throw new BizException(400, "该读者已在预约队列中");
        }
        Reservation reservation = new Reservation();
        reservation.setBookId(book.getId());
        reservation.setReaderId(reader.getId());
        reservation.setStatus("WAITING");
        reservationMapper.insert(reservation);
        auditService.log("RESERVATION_CREATE", "reservation", reservation.getId(),
                reader.getName() + " 预约《" + book.getTitle() + "》");
        return reservation;
    }

    @Transactional
    public void cancel(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BizException(404, "预约不存在");
        }
        if (!"WAITING".equals(reservation.getStatus()) && !"NOTIFIED".equals(reservation.getStatus())) {
            throw new BizException(400, "该预约已结束");
        }
        Reservation update = new Reservation();
        update.setId(id);
        update.setStatus("CANCELLED");
        reservationMapper.updateById(update);
        auditService.log("RESERVATION_CANCEL", "reservation", id, "取消预约");
    }

}
