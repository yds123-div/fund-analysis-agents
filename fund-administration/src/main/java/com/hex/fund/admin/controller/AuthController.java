package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.auth.AuthService;
import com.hex.fund.service.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 API：登录、刷新 Token、获取当前用户。
 */
@Tag(name = "Auth", description = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String token = authService.login(body.get("username"), body.get("password"));
        // 解析 token 获取 userId 再查用户信息
        var claims = com.hex.fund.common.security.JwtUtil.parseToken(
                authService.getJwtSecret(), token);
        User user = authService.getUserById(Long.valueOf(claims.getSubject()));
        user.setPasswordHash(null); // 脱敏
        return ApiResponse.ok(Map.of("token", token, "user", user));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh(@RequestHeader("Authorization") String auth) {
        String oldToken = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        String newToken = authService.refreshToken(oldToken);
        return ApiResponse.ok(Map.of("token", newToken));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<User> me() {
        User user = authService.getUserById(SecurityContext.getCurrentUserId());
        if (user != null) user.setPasswordHash(null);
        return ApiResponse.ok(user);
    }
}
