package com.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 图书 */
@Data
@TableName("book")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String category;
    private String location;
    private Integer totalCopies;
    private Integer availableCopies;
    private BigDecimal price;
    private Integer publishYear;
    /** ACTIVE / OFF_SHELF */
    private String status;
    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
