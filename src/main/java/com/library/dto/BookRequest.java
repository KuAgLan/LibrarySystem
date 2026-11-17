package com.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BookRequest(
        @NotBlank(message = "ISBN 不能为空") @Size(max = 20, message = "ISBN 过长") String isbn,
        @NotBlank(message = "书名不能为空") @Size(max = 200, message = "书名过长") String title,
        @Size(max = 100, message = "作者过长") String author,
        @Size(max = 100, message = "出版社过长") String publisher,
        @Size(max = 40, message = "分类过长") String category,
        @Size(max = 40, message = "馆藏位置过长") String location,
        @Min(value = 0, message = "馆藏数不能为负") Integer totalCopies,
        BigDecimal price,
        Integer publishYear,
        String status,
        @Size(max = 300, message = "备注过长") String remark) {
}
