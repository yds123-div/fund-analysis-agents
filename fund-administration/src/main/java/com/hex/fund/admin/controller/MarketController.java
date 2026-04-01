package com.hex.fund.admin.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.agent.scanner.FundScreenerAgent;
import com.hex.fund.agent.scanner.MarketScannerAgent;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.ai.AiModelService;
import com.hex.fund.service.entity.FundRecommendation;
import com.hex.fund.service.mapper.FundRecommendationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 市场扫描与智能选基 API，结果持久化到推荐记录表。
 */
@Slf4j
@Tag(name = "Market", description = "市场扫描与智能选基")
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final AiModelService aiModelService;
    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final FundRecommendationMapper recommendationMapper;

    @Operation(summary = "市场温度扫描")
    @PostMapping("/scan")
    public ApiResponse<String> scan() {
        var provider = aiModelService.resolveAgentProvider("default", "quick_think");
        MarketScannerAgent scanner = new MarketScannerAgent();
        scanner.configure(llmService, promptLoader, provider.type(), provider.baseUrl(), provider.apiKey(), provider.modelId());
        String marketData = fetchMarketData();
        String result = scanner.scan(marketData);
        recommendationMapper.insert(FundRecommendation.builder()
                .userId(SecurityContext.getCurrentUserId())
                .batchNo("SCAN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .marketTemperature(extractTemperature(result))
                .marketAnalysis(result).reportContent(result)
                .createdAt(LocalDateTime.now()).build());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取最近一次市场扫描记录")
    @GetMapping("/scan/latest")
    public ApiResponse<FundRecommendation> latestScan() {
        List<FundRecommendation> list = recommendationMapper.selectList(
                new LambdaQueryWrapper<FundRecommendation>()
                        .isNotNull(FundRecommendation::getMarketAnalysis)
                        .like(FundRecommendation::getBatchNo, "SCAN-")
                        .orderByDesc(FundRecommendation::getCreatedAt)
                        .last("LIMIT 1"));
        return ApiResponse.ok(list.isEmpty() ? null : list.get(0));
    }

    @Operation(summary = "智能选基推荐")
    @PostMapping("/screen")
    public ApiResponse<String> screen(@RequestParam(defaultValue = "中等风险") String riskPreference) {
        var provider = aiModelService.resolveAgentProvider("default", "deep_think");
        MarketScannerAgent scanner = new MarketScannerAgent();
        scanner.configure(llmService, promptLoader, provider.type(), provider.baseUrl(), provider.apiKey(), provider.modelId());
        String marketData = fetchMarketData();
        String marketTemp = scanner.scan(marketData);
        FundScreenerAgent screener = new FundScreenerAgent();
        screener.configure(llmService, promptLoader, provider.type(), provider.baseUrl(), provider.apiKey(), provider.modelId());
        String result = screener.screen(marketTemp, riskPreference, null);
        recommendationMapper.insert(FundRecommendation.builder()
                .userId(SecurityContext.getCurrentUserId()).batchNo(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .marketTemperature(extractTemperature(marketTemp))
                .marketAnalysis(marketTemp).reportContent(result)
                .createdAt(LocalDateTime.now()).build());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "推荐历史")
    @GetMapping("/recommendations")
    public ApiResponse<List<FundRecommendation>> listRecommendations(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safeOffset = Math.max(0, (page - 1) * safeSize);
        return ApiResponse.ok(recommendationMapper.selectList(
                new LambdaQueryWrapper<FundRecommendation>()
                        .orderByDesc(FundRecommendation::getCreatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + safeOffset)));
    }

    /**
     * 从 LLM 返回的扫描结果中提取市场温度描述。
     */
    private String extractTemperature(String scanResult) {
        if (scanResult == null || scanResult.isBlank()) return null;
        try {
            String json = scanResult.contains("```") ? scanResult.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```", "").trim() : scanResult;
            JSONObject obj = JSONUtil.parseObj(json);
            String level = obj.getStr("level");
            if (level != null && !level.isBlank()) return level;
            Integer temp = obj.getInt("temperature");
            if (temp != null) return temp <= 30 ? "低迷" : temp <= 60 ? "适中" : temp <= 80 ? "偏热" : "过热";
        } catch (Exception ignored) {
        }
        // 从文本中匹配关键词
        if (scanResult.contains("过热")) return "过热";
        if (scanResult.contains("偏热")) return "偏热";
        if (scanResult.contains("低迷")) return "低迷";
        if (scanResult.contains("适中") || scanResult.contains("震荡")) return "适中";
        return null;
    }

    /**
     * 从东方财富获取主要指数和成交量数据，供 LLM 分析。
     */
    private String fetchMarketData() {
        try {
            // 主要指数: 上证、深证、创业板、科创50、沪深300、中证500
            String url = "https://push2.eastmoney.com/api/qt/ulist.np/get"
                    + "?fields=f2,f3,f4,f5,f6,f7,f12,f14&secids=1.000001,0.399001,0.399006,1.000688,1.000300,1.000905";
            String resp = HttpUtil.get(url, 8000);
            JSONObject root = JSONUtil.parseObj(resp);
            var data = root.getJSONObject("data");
            if (data == null) return "暂无详细数据";
            var diff = data.getJSONArray("diff");
            StringBuilder sb = new StringBuilder("【实时市场数据】\n");
            for (int i = 0; i < diff.size(); i++) {
                JSONObject item = diff.getJSONObject(i);
                String name = item.getStr("f14");
                double price = item.getDouble("f2", 0d) / 100.0;
                double changePct = item.getDouble("f3", 0d) / 100.0;
                double change = item.getDouble("f4", 0d) / 100.0;
                double amplitude = item.getDouble("f7", 0d) / 100.0;
                double volume = item.getDouble("f5", 0d);   // 成交量(手)
                double turnover = item.getDouble("f6", 0d);  // 成交额(元)
                sb.append(String.format("%s: %.2f (%.2f%%), 振幅%.2f%%, 成交额%.0f亿\n",
                        name, price, changePct, amplitude, turnover / 1_0000_0000));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取市场数据失败: {}", e.getMessage());
            return "暂无详细数据";
        }
    }
}
