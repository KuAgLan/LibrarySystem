package com.library.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;

/** 统一异常出口 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> biz(BizException e) {
        return ResponseEntity.status(e.getStatus()).body(R.fail(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> validation(BindException e) {
        String msg = Optional.ofNullable(e.getBindingResult().getFieldError())
                .map(err -> err.getDefaultMessage()).orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(R.fail(400, "参数格式不正确：" + e.getName()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.fail(403, "没有操作权限"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(404, "接口不存在：" + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> other(Exception e) {
        log.error("接口异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误：" + e.getMessage()));
    }
}
