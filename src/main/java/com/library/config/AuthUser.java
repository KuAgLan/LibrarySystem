package com.library.config;

/**
 * 当前登录人。
 *
 * @param actor STAFF（管理员账号 sys_user）或 READER（读者账号 reader）
 */
public record AuthUser(Long id, String username, String name, String role, String actor) {

    public static final String ACTOR_STAFF = "STAFF";
    public static final String ACTOR_READER = "READER";

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role) && ACTOR_STAFF.equals(actor);
    }

    public boolean isReader() {
        return ACTOR_READER.equals(actor);
    }
}