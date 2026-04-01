package com.hex.fund.admin.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.entity.Notification;
import com.hex.fund.service.entity.NotificationChannel;
import com.hex.fund.service.mapper.NotificationChannelMapper;
import com.hex.fund.service.mapper.NotificationMapper;
import com.hex.fund.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知渠道配置 API，支持多 Bark 设备和多邮箱管理。
 */
@Tag(name = "Notification", description = "通知渠道管理")
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationChannelMapper channelMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    @Operation(summary = "查询通知渠道")
    @GetMapping("/channels")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<NotificationChannel> rows = channelMapper.selectList(
                new LambdaQueryWrapper<NotificationChannel>()
                        .eq(NotificationChannel::getUserId, SecurityContext.getCurrentUserId()));
        List<Map<String, Object>> result = rows.stream().map(this::toFlatView).toList();
        return ApiResponse.ok(result);
    }

    @Operation(summary = "保存通知渠道")
    @PostMapping("/channels")
    public ApiResponse<Void> save(@RequestBody ChannelDTO dto) {
        NotificationChannel channel = toEntity(dto);
        channel.setUserId(SecurityContext.getCurrentUserId());
        if (channel.getId() != null) channelMapper.updateById(channel);
        else channelMapper.insert(channel);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除通知渠道")
    @DeleteMapping("/channels/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "测试通知渠道")
    @PostMapping("/channels/{id}/test")
    public ApiResponse<String> test(@PathVariable Long id) {
        NotificationChannel channel = channelMapper.selectById(id);
        if (channel == null) return ApiResponse.fail(404, "渠道不存在");
        return ApiResponse.ok(notificationService.testChannel(channel));
    }

    @Operation(summary = "推送历史")
    @GetMapping("/history")
    public ApiResponse<List<Notification>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(0, (page - 1) * safeSize);
        var wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, SecurityContext.getCurrentUserId())
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        return ApiResponse.ok(notificationMapper.selectList(wrapper));
    }

    private NotificationChannel toEntity(ChannelDTO dto) {
        NotificationChannel ch = new NotificationChannel();
        ch.setId(dto.getId());
        ch.setChannelType(dto.getType() != null ? dto.getType().toUpperCase() : "BARK");
        ch.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        JSONObject config = new JSONObject();
        if ("bark".equalsIgnoreCase(dto.getType())) {
            config.set("name", dto.getName());
            config.set("serverUrl", dto.getServerUrl());
            config.set("deviceKey", dto.getDeviceKey());
        } else {
            config.set("email", dto.getEmail());
        }
        ch.setConfigJson(config.toString());
        return ch;
    }

    private Map<String, Object> toFlatView(NotificationChannel ch) {
        JSONObject config = JSONUtil.parseObj(ch.getConfigJson());
        JSONObject view = new JSONObject();
        view.set("id", ch.getId());
        view.set("type", ch.getChannelType() != null ? ch.getChannelType().toLowerCase() : "");
        view.set("enabled", ch.getEnabled() != null && ch.getEnabled() == 1);
        config.forEach(view::set);
        return view;
    }

    /**
     * 前端扁平 DTO，兼容 boolean enabled
     */
    @Data
    static class ChannelDTO {
        private Long id;
        private String type;
        private String name;
        private String serverUrl;
        private String deviceKey;
        private String email;
        private Boolean enabled;
    }
}
