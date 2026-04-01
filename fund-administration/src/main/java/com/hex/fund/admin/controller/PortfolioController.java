package com.hex.fund.admin.controller;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.common.util.ConversionUtil;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.util.FundUtil;
import com.hex.fund.service.ai.AiModelService;
import com.hex.fund.service.entity.FundPortfolio;
import com.hex.fund.service.portfolio.PortfolioService;
import com.hex.fund.service.portfolio.PortfolioService.PortfolioPnL;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓管理 API，含收益计算和持仓分析。
 */
@Slf4j
@Tag(name = "Portfolio", description = "持仓管理与收益计算")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private static final String AI_ANALYSIS_KEY = "portfolio:ai-analysis:";
    private static final String FORECAST_KEY = "portfolio:forecast:";
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PortfolioService portfolioService;
    private final DataSourceManager dataSourceManager;
    private final LlmService llmService;
    private final AiModelService aiModelService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "查询持仓列表")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        var items = portfolioService.listByUser(SecurityContext.getCurrentUserId());
        return ApiResponse.ok(items.stream().map(this::toPortfolioView).toList());
    }

    /** 添加或更新持仓（支持总金额自动计算） */
    @Operation(summary = "添加或更新持仓（支持总金额自动计算）")
    @PostMapping
    public ApiResponse<Void> addOrUpdate(@RequestBody Map<String, Object> body) {
        Long userId = SecurityContext.getCurrentUserId();
        String fundCode = (String) body.get("fundCode");
        if (fundCode == null || fundCode.isBlank())
            return ApiResponse.fail(400, "基金代码不能为空");
        portfolioService.addOrUpdate(buildPortfolio(userId, fundCode, body));
        return ApiResponse.ok();
    }

    @Operation(summary = "删除持仓")
    @DeleteMapping("/{fundCode}")
    public ApiResponse<Void> remove(@PathVariable String fundCode) {
        portfolioService.remove(SecurityContext.getCurrentUserId(), fundCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "计算持仓收益")
    @GetMapping("/pnl")
    public ApiResponse<List<Map<String, Object>>> pnl() {
        return ApiResponse.ok(portfolioService
                .listByUser(SecurityContext.getCurrentUserId()).stream()
                .map(p -> toPnlView(portfolioService.calculatePnL(p))).toList());
    }

    @Operation(summary = "持仓分析（分布、风格）")
    @GetMapping("/analysis")
    public ApiResponse<Map<String, Object>> analysis() {
        return ApiResponse.ok(portfolioService
                .analyzePortfolio(SecurityContext.getCurrentUserId()));
    }

    @Operation(summary = "持仓收益走势")
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(portfolioService
                .getPortfolioTrend(SecurityContext.getCurrentUserId(), days));
    }

    /** AI 持仓分析建议 */
    @Operation(summary = "AI 持仓分析建议")
    @PostMapping("/ai-analysis")
    public ApiResponse<Map<String, String>> aiAnalysis(
            @RequestParam(defaultValue = "0") BigDecimal budget) {
        Long userId = SecurityContext.getCurrentUserId();
        List<FundPortfolio> items = portfolioService.listByUser(userId);
        if (items.isEmpty()) return ApiResponse.fail(400, "暂无持仓数据");
        String prompt = buildAiAnalysisPrompt(items, budget, userId);
        String result = callLlm("deep_think", buildAiAnalysisSystemPrompt(), prompt);
        return ApiResponse.ok(cacheAndReturn(
                AI_ANALYSIS_KEY + userId, "content", result, Duration.ofDays(7)));
    }

    @Operation(summary = "获取最近一次 AI 持仓分析")
    @GetMapping("/ai-analysis/latest")
    public ApiResponse<Map<String, String>> latestAiAnalysis() {
        return readCachedResult(
                AI_ANALYSIS_KEY + SecurityContext.getCurrentUserId(), "content");
    }

    /** 持仓未来盈利预估 */
    @Operation(summary = "持仓未来盈利预估")
    @GetMapping("/forecast")
    public ApiResponse<Map<String, Object>> forecast() {
        Long userId = SecurityContext.getCurrentUserId();
        var cached = readCachedForecast(FORECAST_KEY + userId);
        if (cached != null) return cached;
        List<FundPortfolio> items = portfolioService.listByUser(userId);
        if (items.isEmpty()) return ApiResponse.fail(400, "暂无持仓数据");
        String result = callLlm("quick_think",
                buildForecastSystemPrompt(), buildForecastPrompt(items));
        String time = LocalDateTime.now().format(DT_FMT);
        cacheForecast(FORECAST_KEY + userId, result, time);
        return ApiResponse.ok(Map.of("data", result, "time", time));
    }

    // ---- 视图转换 ----

    private Map<String, Object> toPortfolioView(FundPortfolio p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("fundCode", p.getFundCode());
        map.put("fundName", FundUtil.resolveFundName(dataSourceManager, p.getFundCode()));
        map.put("holdingAmount", p.getHoldingAmount());
        map.put("avgCost", p.getAvgCost());
        map.put("notes", p.getNotes());
        map.put("autoDip", p.getAutoDip() != null && p.getAutoDip());
        map.put("dipAmount", p.getDipAmount());
        map.put("dipFrequency", p.getDipFrequency());
        map.put("createdAt", p.getCreatedAt());
        return map;
    }

    private Map<String, Object> toPnlView(PortfolioPnL pnl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fundCode", pnl.fundCode());
        map.put("fundName", FundUtil.resolveFundName(dataSourceManager, pnl.fundCode()));
        map.put("currentNav", pnl.currentNav());
        map.put("marketValue", pnl.marketValue());
        map.put("pnl", pnl.pnl());
        map.put("pnlRate", pnl.pnlRate());
        return map;
    }

    // ---- 构建方法 ----

    private FundPortfolio buildPortfolio(Long userId, String fundCode,
                                         Map<String, Object> body) {
        FundPortfolio portfolio = new FundPortfolio();
        portfolio.setUserId(userId);
        portfolio.setFundCode(fundCode.trim());
        portfolio.setNotes((String) body.get("notes"));
        portfolio.setAutoDip(Boolean.TRUE.equals(body.get("autoDip")));
        portfolio.setDipAmount(ConversionUtil.toBigDecimal(body.get("dipAmount")));
        portfolio.setDipFrequency((String) body.get("dipFrequency"));
        fillHoldingByAmount(portfolio, body);
        return portfolio;
    }

    private void fillHoldingByAmount(FundPortfolio portfolio, Map<String, Object> body) {
        BigDecimal totalAmount = ConversionUtil.toBigDecimal(body.get("totalAmount"));
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            boolean includeTodayReturn = Boolean.TRUE.equals(body.get("includeTodayReturn"));
            BigDecimal nav = portfolioService.getCurrentNav(
                    portfolio.getFundCode(), includeTodayReturn);
            if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0) {
                portfolio.setAvgCost(nav);
                portfolio.setHoldingAmount(
                        totalAmount.divide(nav, 2, RoundingMode.HALF_UP));
                portfolio.setPurchaseDate(LocalDateTime.now());
            }
        } else {
            portfolio.setHoldingAmount(
                    ConversionUtil.toBigDecimal(body.get("holdingAmount")));
            portfolio.setAvgCost(ConversionUtil.toBigDecimal(body.get("avgCost")));
        }
    }

    // ---- AI Prompt 构建 ----

    private String buildAiAnalysisPrompt(List<FundPortfolio> items,
                                         BigDecimal budget, Long userId) {
        StringBuilder sb = new StringBuilder("【我的持仓明细】\n");
        BigDecimal totalCost = BigDecimal.ZERO, totalValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        for (FundPortfolio p : items) {
            appendHoldingDetail(sb, p);
            totalCost = totalCost.add(p.getHoldingAmount().multiply(p.getAvgCost()));
            PortfolioPnL pnl = portfolioService.calculatePnL(p);
            if (pnl.marketValue() != null) totalValue = totalValue.add(pnl.marketValue());
            if (pnl.pnl() != null) totalPnl = totalPnl.add(pnl.pnl());
        }
        sb.append(String.format("\n【汇总】总成本: %.2f, 总市值: %.2f, 总盈亏: %.2f\n",
                totalCost, totalValue, totalPnl));
        if (budget.compareTo(BigDecimal.ZERO) > 0)
            sb.append(String.format("【可投入预算】%.2f 元\n", budget));
        appendStyleInfo(sb, userId);
        return sb.toString();
    }

    private void appendHoldingDetail(StringBuilder sb, FundPortfolio p) {
        String name = FundUtil.resolveFundName(dataSourceManager, p.getFundCode());
        PortfolioPnL pnl = portfolioService.calculatePnL(p);
        double pnlRatePct = pnl.pnlRate() != null ? pnl.pnlRate().doubleValue() * 100 : 0;
        sb.append(String.format(
                "- %s(%s): 份额%.2f, 成本%.4f, 当前净值%.4f, 市值%.2f, 盈亏%.2f(%.2f%%)\n",
                name, p.getFundCode(), p.getHoldingAmount(), p.getAvgCost(),
                pnl.currentNav(), pnl.marketValue(), pnl.pnl(), pnlRatePct));
    }

    private void appendStyleInfo(StringBuilder sb, Long userId) {
        Map<String, Object> analysis = portfolioService.analyzePortfolio(userId);
        sb.append(String.format("【持仓集中度】%.1f%%\n",
                ((Number) analysis.getOrDefault("concentration", 0)).doubleValue() * 100));
        var style = (Map<?, ?>) analysis.getOrDefault("styleAnalysis", Map.of());
        sb.append(String.format(
                "【风格分布】成长:%.0f%% 价值:%.0f%% 均衡:%.0f%% 大盘:%.0f%% 中盘:%.0f%% 小盘:%.0f%%\n",
                ConversionUtil.toDouble(style.get("growth")),
                ConversionUtil.toDouble(style.get("value")),
                ConversionUtil.toDouble(style.get("balanced")),
                ConversionUtil.toDouble(style.get("largeCap")),
                ConversionUtil.toDouble(style.get("midCap")),
                ConversionUtil.toDouble(style.get("smallCap"))));
    }

    private String buildAiAnalysisSystemPrompt() {
        return "你是一位专业的基金投资顾问，擅长分析个人持仓组合。"
                + "请根据用户的持仓数据、盈亏情况和可投入预算，给出专业的投资建议。"
                + "要求：1.分析当前持仓的优劣势 2.指出风险点 3.给出具体的调仓建议 "
                + "4.如有预算，给出新增配置建议 5.给出定投建议"
                + "输出使用 Markdown 格式，结构清晰，重点突出。";
    }

    private String buildForecastPrompt(List<FundPortfolio> items) {
        StringBuilder sb = new StringBuilder("【我的持仓】\n");
        for (FundPortfolio p : items) {
            String name = FundUtil.resolveFundName(dataSourceManager, p.getFundCode());
            PortfolioPnL pnl = portfolioService.calculatePnL(p);
            sb.append(String.format("- %s(%s): 份额%.2f, 成本%.4f, 当前净值%.4f\n",
                    name, p.getFundCode(), p.getHoldingAmount(), p.getAvgCost(),
                    pnl.currentNav() != null ? pnl.currentNav() : BigDecimal.ZERO));
        }
        return sb.toString();
    }

    private String buildForecastSystemPrompt() {
        return "你是基金投资预测专家。根据用户持仓数据，预估未来90天的收益走势。"
                + "严格按以下JSON数组格式输出，不要输出其他内容：\n"
                + "[{\"day\":30,\"optimistic\":5.2,\"neutral\":2.1,"
                + "\"pessimistic\":-1.5,\"reason\":\"理由\"},"
                + "{\"day\":60,...},{\"day\":90,...}]\n"
                + "其中 optimistic/neutral/pessimistic 为预估收益率百分比，"
                + "reason 为评估理由（20字以内）。";
    }

    // ---- LLM 调用与缓存 ----

    private String callLlm(String thinkLevel, String systemPrompt, String userPrompt) {
        var provider = aiModelService.resolveAgentProvider("default", thinkLevel);
        return llmService.chat(provider.type(), provider.baseUrl(), provider.apiKey(),
                provider.modelId(), systemPrompt, userPrompt).content();
    }

    private Map<String, String> cacheAndReturn(String key, String field,
                                               String value, Duration ttl) {
        String time = LocalDateTime.now().format(DT_FMT);
        try {
            redisTemplate.opsForHash().put(key, field, value);
            redisTemplate.opsForHash().put(key, "time", time);
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("写入Redis缓存失败: {}", e.getMessage());
        }
        return Map.of(field, value, "time", time);
    }

    private ApiResponse<Map<String, String>> readCachedResult(String key, String field) {
        try {
            String content = (String) redisTemplate.opsForHash().get(key, field);
            if (content == null) return ApiResponse.ok(null);
            String time = (String) redisTemplate.opsForHash().get(key, "time");
            return ApiResponse.ok(Map.of(field, content,
                    "time", time != null ? time : ""));
        } catch (Exception e) {
            log.warn("读取缓存失败: {}", e.getMessage());
            return ApiResponse.ok(null);
        }
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String, Object>> readCachedForecast(String key) {
        try {
            String cached = (String) redisTemplate.opsForHash().get(key, "data");
            String cachedTime = (String) redisTemplate.opsForHash().get(key, "time");
            if (cached != null)
                return ApiResponse.ok(Map.of("data", cached,
                        "time", cachedTime != null ? cachedTime : ""));
        } catch (Exception ignored) {
        }
        return null;
    }

    private void cacheForecast(String key, String data, String time) {
        try {
            redisTemplate.opsForHash().put(key, "data", data);
            redisTemplate.opsForHash().put(key, "time", time);
            redisTemplate.expire(key, Duration.ofHours(12));
        } catch (Exception ignored) {
        }
    }
}
