package com.library.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 读者个人中心：资料 + 额度 + 当前借阅概览 */
public record ReaderProfileVO(Long id, String cardNo, String name, String phone, String email,
                              String type, String department, String status,
                              BigDecimal unpaidFine, Integer borrowedNow, Integer totalBorrow,
                              int maxBorrow, int loanDays,
                              long borrowing, long overdue, long waitingReservations,
                              List<LoanVO> currentLoans, LocalDateTime joinedAt) {
}