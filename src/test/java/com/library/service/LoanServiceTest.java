package com.library.service;

import com.library.common.BizException;
import com.library.dto.BorrowRequest;
import com.library.entity.Book;
import com.library.entity.Loan;
import com.library.entity.Reader;
import com.library.mapper.BookMapper;
import com.library.mapper.LoanMapper;
import com.library.mapper.ReaderMapper;
import com.library.mapper.ReservationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 借阅规则单元测试（Mockito，不依赖数据库）
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock LoanMapper loanMapper;
    @Mock BookMapper bookMapper;
    @Mock ReaderMapper readerMapper;
    @Mock ReservationMapper reservationMapper;
    @Mock SettingService settingService;
    @Mock AuditService auditService;
    @InjectMocks LoanService loanService;

    private Reader reader(int borrowed, String type) {
        Reader r = new Reader();
        r.setId(1L);
        r.setName("测试读者");
        r.setType(type);
        r.setStatus("ACTIVE");
        r.setBorrowedNow(borrowed);
        r.setUnpaidFine(BigDecimal.ZERO);
        return r;
    }

    private Book book(int available) {
        Book b = new Book();
        b.setId(2L);
        b.setTitle("测试图书");
        b.setStatus("ACTIVE");
        b.setTotalCopies(1);
        b.setAvailableCopies(available);
        return b;
    }

    @Test
    @DisplayName("达到借阅上限时拒绝借书")
    void borrow_shouldRejectWhenQuotaExceeded() {
        when(readerMapper.selectById(1L)).thenReturn(reader(5, "STUDENT"));
        when(bookMapper.selectById(2L)).thenReturn(book(1));
        when(settingService.maxBorrow("STUDENT")).thenReturn(5);

        assertThatThrownBy(() -> loanService.borrow(new BorrowRequest(2L, 1L, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("借阅上限");

        verify(bookMapper, never()).decreaseAvailable(anyLong());
    }

    @Test
    @DisplayName("存在逾期未还时拒绝借书")
    void borrow_shouldRejectWhenHasOverdue() {
        when(readerMapper.selectById(1L)).thenReturn(reader(1, "TEACHER"));
        when(bookMapper.selectById(2L)).thenReturn(book(1));
        when(settingService.maxBorrow("TEACHER")).thenReturn(10);
        when(loanMapper.countOverdueByReader(1L)).thenReturn(2L);

        assertThatThrownBy(() -> loanService.borrow(new BorrowRequest(2L, 1L, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("逾期");
    }

    @Test
    @DisplayName("欠费达到阈值时拒绝借书")
    void borrow_shouldRejectWhenFineReachedThreshold() {
        Reader r = reader(0, "PUBLIC");
        r.setUnpaidFine(new BigDecimal("10.00"));
        when(readerMapper.selectById(1L)).thenReturn(r);
        when(bookMapper.selectById(2L)).thenReturn(book(1));
        when(settingService.maxBorrow("PUBLIC")).thenReturn(3);
        when(loanMapper.countOverdueByReader(1L)).thenReturn(0L);
        when(settingService.getDecimal(eq(SettingService.BLOCK_FINE), anyString())).thenReturn(new BigDecimal("10.00"));

        assertThatThrownBy(() -> loanService.borrow(new BorrowRequest(2L, 1L, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("罚金");
    }

    @Test
    @DisplayName("库存为 0 时拒绝借书")
    void borrow_shouldRejectWhenNoCopy() {
        when(readerMapper.selectById(1L)).thenReturn(reader(0, "STUDENT"));
        when(bookMapper.selectById(2L)).thenReturn(book(0));
        when(settingService.maxBorrow("STUDENT")).thenReturn(5);
        when(loanMapper.countOverdueByReader(1L)).thenReturn(0L);
        when(settingService.getDecimal(eq(SettingService.BLOCK_FINE), anyString())).thenReturn(new BigDecimal("10.00"));

        assertThatThrownBy(() -> loanService.borrow(new BorrowRequest(2L, 1L, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可借副本");
    }

    @Test
    @DisplayName("并发下库存被抢走时提示重试")
    void borrow_shouldRejectWhenStockTakenConcurrently() {
        when(readerMapper.selectById(1L)).thenReturn(reader(0, "STUDENT"));
        when(bookMapper.selectById(2L)).thenReturn(book(1));
        when(settingService.maxBorrow("STUDENT")).thenReturn(5);
        when(loanMapper.countOverdueByReader(1L)).thenReturn(0L);
        when(settingService.getDecimal(eq(SettingService.BLOCK_FINE), anyString())).thenReturn(new BigDecimal("10.00"));
        // 条件更新影响 0 行 → 说明被其它事务抢走
        when(bookMapper.decreaseAvailable(2L)).thenReturn(0);

        assertThatThrownBy(() -> loanService.borrow(new BorrowRequest(2L, 1L, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重试");
    }

    @Test
    @DisplayName("逾期还书按天计费且不超过封顶，并归还库存")
    void return_shouldChargeCappedFine() {
        Loan loan = new Loan();
        loan.setId(9L);
        loan.setBookId(2L);
        loan.setReaderId(1L);
        loan.setStatus("BORROWED");
        loan.setBorrowedAt(LocalDateTime.now().minusDays(40));
        loan.setDueAt(LocalDateTime.now().minusDays(10));   // 逾期 10 天

        Reader r = reader(1, "STUDENT");
        r.setUnpaidFine(BigDecimal.ZERO);

        when(loanMapper.selectById(9L)).thenReturn(loan);
        when(settingService.getDecimal(SettingService.FINE_PER_DAY, "0.50")).thenReturn(new BigDecimal("0.50"));
        when(settingService.getDecimal(SettingService.MAX_FINE, "50.00")).thenReturn(new BigDecimal("50.00"));
        when(settingService.getDecimal(SettingService.BLOCK_FINE, "10.00")).thenReturn(new BigDecimal("10.00"));
        when(readerMapper.selectById(1L)).thenReturn(r);
        when(reservationMapper.selectOne(any())).thenReturn(null);

        loanService.returnBook(9L);

        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanMapper).updateById(captor.capture());
        Loan updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("RETURNED");
        assertThat(updated.getFineAmount()).isEqualByComparingTo("5.00");   // 10 天 × 0.5
        verify(bookMapper).increaseAvailable(2L);
        verify(readerMapper).adjustBorrowed(1L, -1, 0);
        verify(readerMapper).addFine(1L, new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("已归还的记录不能重复还书")
    void return_shouldRejectWhenAlreadyReturned() {
        Loan loan = new Loan();
        loan.setId(9L);
        loan.setStatus("RETURNED");
        when(loanMapper.selectById(9L)).thenReturn(loan);

        assertThatThrownBy(() -> loanService.returnBook(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已归还");
    }

    @Test
    @DisplayName("有预约排队时禁止续借")
    void renew_shouldRejectWhenReservationWaiting() {
        Loan loan = new Loan();
        loan.setId(9L);
        loan.setBookId(2L);
        loan.setStatus("BORROWED");
        loan.setDueAt(LocalDateTime.now().plusDays(5));
        loan.setRenewCount(0);

        when(loanMapper.selectById(9L)).thenReturn(loan);
        when(settingService.getInt(SettingService.RENEW_LIMIT, 1)).thenReturn(1);
        when(reservationMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> loanService.renew(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("预约");
    }
}
