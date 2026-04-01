package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 基金信息查询 API。
 */
@Tag(name = "Fund", description = "基金信息查询")
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final DataSourceManager dataSourceManager;

    @Operation(summary = "Get fund basic info")
    @GetMapping("/{code}")
    public ApiResponse<FundBasicData> getFund(@PathVariable String code) {
        return dataSourceManager.getFirstAvailable()
                .map(adapter -> ApiResponse.ok(adapter.getFundBasic(code)))
                .orElse(ApiResponse.fail(50001, "no data source available"));
    }

    @Operation(summary = "Get real-time NAV estimate")
    @GetMapping("/{code}/estimate")
    public ApiResponse<FundEstimate> getEstimate(@PathVariable String code) {
        return dataSourceManager.getFirstAvailable()
                .map(adapter -> ApiResponse.ok(adapter.getRealTimeEstimate(code)))
                .orElse(ApiResponse.fail(50001, "no data source available"));
    }

    @Operation(summary = "Get NAV history")
    @GetMapping("/{code}/nav")
    public ApiResponse<List<NavData>> getNavHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        return dataSourceManager.getFirstAvailable()
                .map(adapter -> ApiResponse.ok(adapter.getNavHistory(code, start, end)))
                .orElse(ApiResponse.fail(50001, "no data source available"));
    }

    @Operation(summary = "搜索基金")
    @GetMapping("/search")
    public ApiResponse<List<FundBasicData>> search(@RequestParam String keyword) {
        return dataSourceManager.getFirstAvailable()
                .map(adapter -> ApiResponse.ok(adapter.searchFunds(keyword)))
                .orElse(ApiResponse.fail(50001, "no data source available"));
    }

    @Operation(summary = "获取基金持仓")
    @GetMapping("/{code}/holdings")
    public ApiResponse<List<HoldingData>> getHoldings(@PathVariable String code) {
        return ApiResponse.ok(dataSourceManager.getAggregatedHoldings(code));
    }

    @Operation(summary = "获取基金经理信息")
    @GetMapping("/{code}/manager")
    public ApiResponse<FundManagerData> getManager(@PathVariable String code) {
        FundManagerData manager = dataSourceManager.getAggregatedManager(code);
        return manager != null ? ApiResponse.ok(manager) : ApiResponse.fail(404, "基金经理信息不可用");
    }

    @Operation(summary = "获取基金完整详情（基本信息+估值+经理+持仓）")
    @GetMapping("/{code}/detail")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable String code) {
        var adapter = dataSourceManager.getFirstAvailable().orElse(null);
        if (adapter == null) return ApiResponse.fail(50001, "no data source available");
        return ApiResponse.ok(Map.of(
                "basic", adapter.getFundBasic(code),
                "estimate", adapter.getRealTimeEstimate(code) != null ? adapter.getRealTimeEstimate(code) : Map.of(),
                "manager", adapter.getFundManager(code) != null ? adapter.getFundManager(code) : Map.of(),
                "holdings", adapter.getFundHoldings(code, null)
        ));
    }
}
