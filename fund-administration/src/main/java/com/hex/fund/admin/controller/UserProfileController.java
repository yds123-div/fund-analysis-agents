package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.entity.UserProfile;
import com.hex.fund.service.profile.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 投资画像配置 API。
 */
@Tag(name = "Profile", description = "投资画像管理")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    @Operation(summary = "查询投资画像")
    @GetMapping
    public ApiResponse<UserProfile> get() {
        UserProfile profile = profileService.getByUser(SecurityContext.getCurrentUserId());
        return ApiResponse.ok(profile);
    }

    @Operation(summary = "保存投资画像")
    @PostMapping
    public ApiResponse<Void> save(@RequestBody UserProfile profile) {
        profile.setUserId(SecurityContext.getCurrentUserId());
        profileService.saveOrUpdate(profile);
        return ApiResponse.ok();
    }
}
