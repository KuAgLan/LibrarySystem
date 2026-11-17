package com.library.config;

/** 当前登录人 */
public record AuthUser(Long id, String username, String name, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
