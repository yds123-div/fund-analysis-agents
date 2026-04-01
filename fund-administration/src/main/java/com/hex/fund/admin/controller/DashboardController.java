package com.hex.fund.admin.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.service.entity.TaskExecution;
import com.hex.fund.service.mapper.FundPortfolioMapper;
import com.hex.fund.service.mapper.TaskExecutionMapper;
import com.hex.fund.service.mapper.WatchListMapper;
import com.hex.fund.service.portfolio.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 仪表盘数据聚合 API，提供市场概览、持仓统计、任务统计等。
 */
@Slf4j
@Tag(name = "Dashboard", description = "仪表盘数据")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final String MARKET_CACHE_KEY = "dashboard:market:indices";
    private final DataSourceManager dataSourceManager;
    private final TaskExecutionMapper executionMapper;
    private final WatchListMapper watchListMapper;
    private final FundPortfolioMapper portfolioMapper;
    private final PortfolioService portfolioService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "仪表盘概览数据")
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Long userId = SecurityContext.getCurrentUserId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("marketIndices", fetchMarketIndices());
        data.put("taskStats", getTaskStats());
        data.put("portfolioSummary", getPortfolioSummary(userId));
        data.put("dataSources", dataSourceManager.healthCheck());
        return ApiResponse.ok(data);
    }

    /**
     * 从东方财富免费接口获取主要指数行情，带 Redis 缓存。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchMarketIndices() {
        // 先读缓存（5分钟有效）
        try {
            String cached = redisTemplate.opsForValue().get(MARKET_CACHE_KEY);
            if (cached != null) return (List<Map<String, Object>>) (List<?>) JSONUtil.toList(cached, Map.class);
        } catch (Exception e) {
            log.warn("读取市场指数缓存失败: {}", e.getMessage());
        }
        try {
            String url = "https://push2.eastmoney.com/api/qt/ulist.np/get?fields=f2,f3,f4,f12,f14&secids=1.000001,0.399001,0.399006";
            String resp = HttpUtil.get(url, 5000);
            JSONObject root = JSONUtil.parseObj(resp);
            var diff = root.getJSONObject("data").getJSONArray("diff");
            List<Map<String, Object>> indices = new ArrayList<>();
            for (int i = 0; i < diff.size(); i++) {
                JSONObject item = diff.getJSONObject(i);
                double price = item.getDouble("f2", 0d) / 100.0;
                double changePct = item.getDouble("f3", 0d) / 100.0;
                double change = item.getDouble("f4", 0d) / 100.0;
                indices.add(Map.of(
                        "code", item.getStr("f12"), "name", item.getStr("f14"),
                        "price", price, "changePercent", changePct, "change", change));
            }
            try {
                redisTemplate.opsForValue().set(MARKET_CACHE_KEY, JSONUtil.toJsonStr(indices), Duration.ofMinutes(5));
            } catch (Exception e) {
                log.warn("写入市场指数缓存失败: {}", e.getMessage());
            }
            return indices;
        } catch (Exception e) {
            log.warn("获取市场指数失败: {}", e.getMessage());
            return getDefaultIndices();
        }
    }

    private List<Map<String, Object>> getDefaultIndices() {
        return List.of(
                Map.of("code", "000001", "name", "上证指数", "price", "-", "changePercent", 0, "change", 0),
                Map.of("code", "399001", "name", "深证成指", "price", "-", "changePercent", 0, "change", 0),
                Map.of("code", "399006", "name", "创业板指", "price", "-", "changePercent", 0, "change", 0));
    }

    private Map<String, Object> getTaskStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayTotal = executionMapper.selectCount(new LambdaQueryWrapper<TaskExecution>()
                .ge(TaskExecution::getCreatedAt, todayStart));
        long todaySuccess = executionMapper.selectCount(new LambdaQueryWrapper<TaskExecution>()
                .ge(TaskExecution::getCreatedAt, todayStart).eq(TaskExecution::getStatus, "SUCCESS"));
        long running = executionMapper.selectCount(new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getStatus, "RUNNING"));
        return Map.of("todayTotal", todayTotal, "todaySuccess", todaySuccess, "running", running);
    }

    private Map<String, Object> getPortfolioSummary(Long userId) {
        var portfolios = portfolioService.listByUser(userId);
        if (portfolios.isEmpty()) return Map.of("totalValue", 0, "totalPnl", 0, "count", 0);
        var pnls = portfolios.stream().map(portfolioService::calculatePnL).toList();
        var totalValue = pnls.stream().map(PortfolioService.PortfolioPnL::marketValue)
                .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var totalPnl = pnls.stream().map(PortfolioService.PortfolioPnL::pnl)
                .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return Map.of("totalValue", totalValue, "totalPnl", totalPnl, "count", portfolios.size());
    }
}