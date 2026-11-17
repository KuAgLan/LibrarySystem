package com.library.service;

import com.library.config.AuthUser;
import com.library.entity.AuditLog;
import com.library.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作审计 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    public void log(String action, String targetType, Long targetId, String detail) {
        AuthUser user = current();
        AuditLog log = new AuditLog();
        log.setUserId(user == null ? null : user.id());
        log.setUserName(user == null ? "system" : user.name());
        log.setAction(action);
        log.setTargetType(targetType == null ? "" : targetType);
        log.setTargetId(targetId);
        log.setDetail(detail == null ? "" : detail);
        log.setIp(clientIp());
        auditLogMapper.insert(log);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String remote = request.getRemoteAddr();
            return remote == null ? "" : remote.replace("::ffff:", "");
        }
        return "";
    }
}
