package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 读者 */
@Data
@TableName("reader")
public class Reader {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String cardNo;
    private String name;
    private String phone;
    private String email;
    /** STUDENT / TEACHER / PUBLIC */
    private String type;
    private String department;
    /** ACTIVE / SUSPENDED */
    private String status;
    private BigDecimal unpaidFine;
    private Integer borrowedNow;
    private Integer totalBorrow;
    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
