package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.common.BizException;
import com.library.config.AuthUser;
import com.library.config.JwtUtil;
import com.library.dto.LoginRequest;
import com.library.entity.Reader;
import com.library.entity.SysUser;
import com.library.mapper.ReaderMapper;
import com.library.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录：管理员（sys_user）与读者（reader.card_no）共用同一入口，
 * 登录后由 JWT 中的 role（ADMIN/READER）+ actor（STAFF/READER）区分身份与可用接口。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int FAIL_LIMIT = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;

    private final SysUserMapper userMapper;
    private final ReaderMapper readerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> firstFailAt = new ConcurrentHashMap<>();

    public record LoginResult(String token, UserInfo user) {
    }

    public record UserInfo(Long id, String username, String name, String role, String actor,
                           String cardNo, String readerType, java.math.BigDecimal unpaidFine) {
    }

    public LoginResult login(LoginRequest request, String ip) {
        String key = ip + ":" + request.username().toLowerCase();
        assertNotLocked(key);

        SysUser staff = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()).last("limit 1"));
        if (staff != null && passwordEncoder.matches(request.password(), staff.getPassword())) {
            if (!"ACTIVE".equals(staff.getStatus())) {
                throw new BizException(403, "该账号已被停用");
            }
            clearFailure(key);
            SysUser update = new SysUser();
            update.setId(staff.getId());
            update.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(update);
            auditService.log("AUTH_LOGIN", "user", staff.getId(), "管理员登录");
            String token = jwtUtil.create(staff.getId(), staff.getUsername(), staff.getName(), staff.getRole(),
                    AuthUser.ACTOR_STAFF);
            return new LoginResult(token, new UserInfo(staff.getId(), staff.getUsername(), staff.getName(),
                    staff.getRole(), AuthUser.ACTOR_STAFF, null, null, null));
        }

        Reader reader = readerMapper.selectOne(Wrappers.<Reader>lambdaQuery()
                .eq(Reader::getCardNo, request.username()).last("limit 1"));
        if (reader != null && reader.getPassword() != null && !reader.getPassword().isBlank()
                && passwordEncoder.matches(request.password(), reader.getPassword())) {
            if (!"ACTIVE".equals(reader.getStatus())) {
                throw new BizException(403, "该读者证已被停用，请联系管理员");
            }
            clearFailure(key);
            auditService.log("AUTH_LOGIN", "reader", reader.getId(), "读者登录（" + reader.getCardNo() + "）");
            String token = jwtUtil.create(reader.getId(), reader.getCardNo(), reader.getName(), "READER",
                    AuthUser.ACTOR_READER);
            return new LoginResult(token, new UserInfo(reader.getId(), reader.getCardNo(), reader.getName(),
                    "READER", AuthUser.ACTOR_READER, reader.getCardNo(), reader.getType(), reader.getUnpaidFine()));
        }

        noteFailure(key);
        throw new BizException(401, "用户名或密码错误");
    }

    public UserInfo me(AuthUser user) {
        if (user == null) {
            throw new BizException(401, "登录状态已失效");
        }
        if (user.isReader()) {
            Reader reader = readerMapper.selectById(user.id());
            if (reader == null) {
                throw new BizException(401, "读者不存在");
            }
            return new UserInfo(reader.getId(), reader.getCardNo(), reader.getName(), "READER",
                    AuthUser.ACTOR_READER, reader.getCardNo(), reader.getType(), reader.getUnpaidFine());
        }
        SysUser staff = userMapper.selectById(user.id());
        if (staff == null) {
            throw new BizException(401, "账号不存在");
        }
        return new UserInfo(staff.getId(), staff.getUsername(), staff.getName(), staff.getRole(),
                AuthUser.ACTOR_STAFF, null, null, null);
    }

    public void changePassword(AuthUser user, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        if (user.isReader()) {
            Reader reader = readerMapper.selectById(user.id());
            if (reader == null) {
                throw new BizException(401, "读者不存在");
            }
            if (reader.getPassword() != null && !reader.getPassword().isBlank()
                    && !passwordEncoder.matches(oldPassword == null ? "" : oldPassword, reader.getPassword())) {
                throw new BizException(400, "原密码不正确");
            }
            Reader update = new Reader();
            update.setId(reader.getId());
            update.setPassword(passwordEncoder.encode(newPassword));
            readerMapper.updateById(update);
            auditService.log("AUTH_PASSWORD", "reader", reader.getId(), "读者修改密码");
            return;
        }
        SysUser staff = userMapper.selectById(user.id());
        if (staff == null) {
            throw new BizException(401, "账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, staff.getPassword())) {
            throw new BizException(400, "原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(staff.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        auditService.log("AUTH_PASSWORD", "user", staff.getId(), "修改本人密码");
    }

    private void assertNotLocked(String key) {
        Long first = firstFailAt.get(key);
        int[] count = failures.get(key);
        if (first == null || count == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - first;
        if (elapsed > LOCK_MILLIS) {
            clearFailure(key);
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

    private void clearFailure(String key) {
        failures.remove(key);
        firstFailAt.remove(key);
    }
}