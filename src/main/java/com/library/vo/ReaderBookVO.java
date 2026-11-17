package com.library.vo;

import java.math.BigDecimal;

/** 读者门户看到的图书信息（不含内部备注） */
public record ReaderBookVO(Long id, String isbn, String title, String author, String publisher,
                           String category, String location, Integer availableCopies,
                           Integer totalCopies, Integer publishYear, BigDecimal price) {
}