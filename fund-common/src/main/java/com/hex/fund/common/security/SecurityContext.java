package com.hex.fund.common.security;

/**
 * 基于 ThreadLocal 的安全上下文，存储当前请求的用户信息。
 */
public final class SecurityContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static void setCurrentUser(Long userId, String username, String role) {
        HOLDER.set(new UserInfo(userId, username, role));
    }

    public static Long getCurrentUserId() {
        UserInfo info = HOLDER.get();
        return info != null ? info.userId() : null;
    }

    public static String getCurrentUsername() {
        UserInfo info = HOLDER.get();
        return info != null ? info.username() : null;
    }

    public static String getCurrentRole() {
        UserInfo info = HOLDER.get();
        return info != null ? info.role() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private record UserInfo(Long userId, String username, String role) {
    }
}
