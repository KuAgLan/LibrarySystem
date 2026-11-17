package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.common.BizException;
import com.library.config.AuthUser;
import com.library.config.JwtUtil;
import com.library.dto.LoginRequest;
import com.library.entity.SysUser;
import com.library.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 登录、当前用户、改密（含失败限流） */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int FAIL_LIMIT = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> firstFailAt = new ConcurrentHashMap<>();

    public record LoginResult(String token, UserInfo user) {
    }

    public record UserInfo(Long id, String username, String name, String role) {
    }

    public LoginResult login(LoginRequest request, String ip) {
        String key = ip + ":" + request.username().toLowerCase();
        assertNotLocked(key);
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()).last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            noteFailure(key);
            throw new BizException(401, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(403, "该账号已被停用");
        }
        failures.remove(key);
        firstFailAt.remove(key);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
        auditService.log("AUTH_LOGIN", "user", user.getId(), "登录成功（" + user.getRole() + "）");

        String token = jwtUtil.create(user.getId(), user.getUsername(), user.getName(), user.getRole());
        return new LoginResult(token, new UserInfo(user.getId(), user.getUsername(), user.getName(), user.getRole()));
    }

    public UserInfo me(AuthUser user) {
        SysUser row = userMapper.selectById(user.id());
        if (row == null) {
            throw new BizException(401, "账号不存在");
        }
        return new UserInfo(row.getId(), row.getUsername(), row.getName(), row.getRole());
    }

    public void changePassword(AuthUser user, String oldPassword, String newPassword) {
        SysUser row = userMapper.selectById(user.id());
        if (row == null) {
            throw new BizException(401, "账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, row.getPassword())) {
            throw new BizException(400, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        SysUser update = new SysUser();
        update.setId(row.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        auditService.log("AUTH_PASSWORD", "user", row.getId(), "修改本人密码");
    }

    private void assertNotLocked(String key) {
        Long first = firstFailAt.get(key);
        int[] count = failures.get(key);
        if (first == null || count == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - first;
        if (elapsed > LOCK_MILLIS) {
            failures.remove(key);
            firstFailAt.remove(key);
            return;
        }
        if (count[0] >= FAIL_LIMIT) {
            throw new BizException(429, "登录失败次数过多，请 " + Math.max(1, (LOCK_MILLIS - elapsed) / 60000) + " 分钟后再试");
        }
    }

    private void noteFailure(String key) {
        firstFailAt.putIfAbsent(key, System.currentTimeMillis());
        failures.computeIfAbsent(key, k -> new int[]{0})[0]++;
    }
}
