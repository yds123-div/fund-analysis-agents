package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.datasource.core.DataSourceManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统健康检查与数据源状态 API。
 */
@Tag(name = "Health", description = "System health APIs")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSourceManager dataSourceManager;

    @Operation(summary = "System health check")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Boolean> dsHealth = dataSourceManager.healthCheck();
        return ApiResponse.ok(Map.of("status", "UP", "dataSources", dsHealth));
    }
}
