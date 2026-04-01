package com.hex.fund.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.service.entity.DatasourceConfig;
import com.hex.fund.service.entity.SystemConfig;
import com.hex.fund.service.mapper.DatasourceConfigMapper;
import com.hex.fund.service.mapper.SystemConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置与数据源配置管理 API。
 */
@Tag(name = "Config", description = "系统配置与数据源管理")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigMapper configMapper;
    private final DatasourceConfigMapper dsMapper;

    @Operation(summary = "查询系统配置")
    @GetMapping("/system")
    public ApiResponse<List<SystemConfig>> listSystemConfig(@RequestParam(required = false) String group) {
        var wrapper = new LambdaQueryWrapper<SystemConfig>();
        if (group != null && !group.isBlank()) wrapper.eq(SystemConfig::getConfigGroup, group);
        return ApiResponse.ok(configMapper.selectList(wrapper));
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/system")
    public ApiResponse<Void> updateSystemConfig(@RequestBody SystemConfig config) {
        configMapper.updateById(config);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询数据源配置")
    @GetMapping("/datasource")
    public ApiResponse<List<DatasourceConfig>> listDatasource() {
        return ApiResponse.ok(dsMapper.selectList(null));
    }

    @Operation(summary = "更新数据源配置")
    @PutMapping("/datasource")
    public ApiResponse<Void> updateDatasource(@RequestBody DatasourceConfig config) {
        dsMapper.updateById(config);
        return ApiResponse.ok();
    }
}
