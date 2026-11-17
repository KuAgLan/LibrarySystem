package com.library.dto;

import jakarta.validation.constraints.NotNull;

public record BorrowRequest(
        @NotNull(message = "请选择图书") Long bookId,
        @NotNull(message = "请选择读者") Long readerId,
        String remark) {
}
