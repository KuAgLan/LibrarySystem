package com.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReaderRequest(
        @NotBlank(message = "读者姓名不能为空") @Size(max = 40, message = "姓名过长") String name,
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
        @Size(max = 80, message = "邮箱过长") String email,
        @NotBlank(message = "请选择读者类型") String type,
        @Size(max = 60, message = "院系/单位过长") String department,
        String status,
        @Size(max = 300, message = "备注过长") String remark) {
}
