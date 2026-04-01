package com.hex.fund.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.EvidenceItem;
import com.hex.fund.agent.model.ModelTrace;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.enums.EvidenceLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分析 Agent 模板方法基类 — 子类只需实现 getSystemPrompt() 和 buildUserPrompt()。
 */
@Slf4j
public abstract class AbstractAnalysisAgent implements AnalysisAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("50");

    protected LlmService llmService;
    protected PromptLoader promptLoader;
    protected String providerType, baseUrl, apiKey, modelId;

    /** 配置 LLM 连接参数 */
    public void configure(LlmService llmService, PromptLoader promptLoader,
                          String providerType, String baseUrl, String apiKey, String modelId) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    @Override
    public AgentReport analyze(AnalysisContext context) {
        log.info("[{}] 开始分析: 基金={}", getAgentId(), context.fundCode());
        try {
            LlmService.LlmResult result = llmService.chat(providerType, baseUrl, apiKey, modelId,
                    getSystemPrompt(), buildUserPrompt(context));
            AgentReport report = parseReport(result.content(), result.trace());
            log.info("[{}] 分析完成: 置信度={}", getAgentId(), report.confidenceScore());
            return report;
        } catch (Exception e) {
            log.error("[{}] 分析失败: {}", getAgentId(), e.getMessage());
            return buildErrorReport(e.getMessage());
        }
    }

    protected abstract String getSystemPrompt();

    protected abstract String buildUserPrompt(AnalysisContext context);

    /** 解析 LLM 响应为 AgentReport，优先尝试 JSON，失败则回退为纯文本 */
    protected AgentReport parseReport(String content, ModelTrace trace) {
        try {
            String json = extractJson(content);
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {});
            return new AgentReport(getAgentId(), getAgentRole(),
                    str(parsed, "summary"), strList(parsed, "keyFindings"),
                    decimal(parsed, "confidenceScore"), str(parsed, "detailedAnalysis"),
                    parsed, buildEvidences(), trace);
        } catch (Exception e) {
            log.warn("[{}] JSON响应解析失败，使用原始文本", getAgentId());
            String summary = content.length() > 200 ? content.substring(0, 200) : content;
            return new AgentReport(getAgentId(), getAgentRole(), summary,
                    List.of(), DEFAULT_CONFIDENCE, content, Map.of(), buildEvidences(), trace);
        }
    }

    protected AgentReport buildErrorReport(String errorMsg) {
        return new AgentReport(getAgentId(), getAgentRole(),
                "Analysis failed: " + errorMsg, List.of(), BigDecimal.ZERO, errorMsg,
                Map.of("error", true), List.of(),
                new ModelTrace(baseUrl, modelId, null, 0, 0, 0));
    }

    protected List<EvidenceItem> buildEvidences() {
        return List.of(new EvidenceItem("llm", "agent_analysis",
                EvidenceLevel.INFERENCE, LocalDateTime.now(), LocalDate.now(), null));
    }

    protected String outputFormatInstruction() {
        return "\n" + promptLoader.load("common/output-format");
    }

    // --- 工具方法 ---
    private String extractJson(String content) {
        content = content.trim();
        if (content.startsWith("```")) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) return content.substring(start, end + 1);
        }
        return content;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private List<String> strList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }

    private BigDecimal decimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return DEFAULT_CONFIDENCE; }
    }
}
