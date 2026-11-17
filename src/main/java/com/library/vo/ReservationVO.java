package com.library.vo;

import java.time.LocalDateTime;

/** 预约队列展示对象 */
public record ReservationVO(Long id, Long bookId, String bookTitle, Long readerId, String readerName,
                            String readerCardNo, String status, LocalDateTime createdAt, LocalDateTime notifiedAt) {
}