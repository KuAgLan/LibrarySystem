package com.library.controller;

import com.library.common.R;
import com.library.config.AuthUser;
import com.library.dto.LoginRequest;
import com.library.service.AuthService;
import com.library.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    @PostMapping("/login")
    public R<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return R.ok(authService.login(request, clientIp(servletRequest)));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        AuthUser user = auditService.current();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("user", authService.me(user));
        data.put("permissions", Map.of("admin", user.isAdmin(), "reader", user.isReader()));
        return R.ok(data);
    }

    @PostMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(auditService.current(), body.get("oldPassword"), body.get("newPassword"));
        return R.success();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote.replace("::ffff:", "");
    }
}
