package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.util.FundUtil;
import com.hex.fund.service.analysis.AnalysisService;
import com.hex.fund.service.entity.AnalysisReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基金分析触发与报告查询 API。
 */
@Tag(name = "Analysis", description = "基金分析与报告管理")
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DataSourceManager dataSourceManager;

    @Operation(summary = "触发基金分析")
    @PostMapping("/{fundCode}")
    public ApiResponse<Map<String, String>> triggerAnalysis(
            @PathVariable String fundCode,
            @RequestParam(defaultValue = "") String fundName) {
        String batchNo = analysisService.triggerAnalysis(fundCode, fundName.isEmpty() ? fundCode : fundName);
        return ApiResponse.ok(Map.of("batchNo", batchNo));
    }

    @Operation(summary = "查询分析报告")
    @GetMapping("/report/{batchNo}")
    public ApiResponse<AnalysisReport> getReport(@PathVariable String batchNo) {
        AnalysisReport report = analysisService.getReport(batchNo);
        if (report == null) return ApiResponse.fail(404, "报告不存在: " + batchNo);
        return ApiResponse.ok(report);
    }

    @Operation(summary = "查询报告历史列表")
    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> listReports(
            @RequestParam(required = false) String fundCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AnalysisReport> reports = analysisService.listReports(fundCode, page, size);
        return ApiResponse.ok(reports.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("fundCode", r.getFundCode());
            map.put("fundName", FundUtil.resolveFundName(dataSourceManager, r.getFundCode()));
            map.put("reportDate", r.getReportDate());
            map.put("reportType", r.getReportType());
            map.put("batchNo", r.getBatchNo());
            map.put("overallRating", r.getOverallRating());
            map.put("overallScore", r.getOverallScore());
            map.put("createdAt", r.getCreatedAt());
            return map;
        }).toList());
    }
}
