package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 借阅记录 */
@Data
@TableName("loan")
public class Loan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long readerId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;
    private Integer renewCount;
    /** BORROWED / RETURNED / LOST */
    private String status;
    private BigDecimal fineAmount;
    private Integer finePaid;
    private String remark;
    private String operator;
    private LocalDateTime createdAt;
}
