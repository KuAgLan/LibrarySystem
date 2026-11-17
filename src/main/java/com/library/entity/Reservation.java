package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 预约排队 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long readerId;
    /** WAITING / NOTIFIED / FULFILLED / CANCELLED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime notifiedAt;
}
