package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.auth.AuthService;
import com.hex.fund.service.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理 API（仅 ADMIN 角色可操作）。
 */
@Tag(name = "User", description = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "用户列表")
    @GetMapping
    public ApiResponse<List<User>> list() {
        requireAdmin();
        List<User> users = authService.listUsers();
        users.forEach(u -> u.setPasswordHash(null));
        return ApiResponse.ok(users);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public ApiResponse<User> create(@RequestBody Map<String, String> body) {
        requireAdmin();
        User user = authService.createUser(body.get("username"), body.get("password"),
                body.get("email"), body.get("phone"), body.get("role"));
        user.setPasswordHash(null);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody User user) {
        requireAdmin();
        user.setId(id);
        user.setPasswordHash(null); // 不允许通过此接口改密码
        authService.updateUser(user);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireAdmin();
        authService.deleteUser(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireAdmin();
        authService.resetPassword(id, body.get("password"));
        return ApiResponse.ok();
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(SecurityContext.getCurrentRole())) {
            throw new com.hex.fund.common.exception.BizException(
                    com.hex.fund.common.exception.ErrorCode.AUTH_ACCESS_DENIED);
        }
    }
}