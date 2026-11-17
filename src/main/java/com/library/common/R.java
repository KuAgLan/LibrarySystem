package com.library.common;

/** 统一响应体 { ok, data, error } */
public record R<T>(boolean ok, T data, ErrorBody error) {

    public record ErrorBody(int status, String message) {
    }

    public static <T> R<T> ok(T data) {
        return new R<>(true, data, null);
    }

    public static R<Void> success() {
        return new R<>(true, null, null);
    }

    public static <T> R<T> fail(int status, String message) {
        return new R<>(false, null, new ErrorBody(status, message));
    }
}
