package com.hex.fund.agent.debate;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 辩论研究员基类 — 封装 configure、summarizeReports 等公共逻辑，
 * 子类只需实现 argue 中差异化的 prompt 加载。
 */
@Slf4j
public abstract class AbstractDebateResearcherAgent {

    protected LlmService llmService;
    protected PromptLoader promptLoader;
    protected String providerType, baseUrl, apiKey, modelId;

    public void configure(LlmService llmService, PromptLoader promptLoader,
                          String providerType, String baseUrl, String apiKey, String modelId) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    /** 执行辩论论证 */
    public abstract String argue(AnalysisContext context, String previousArgument);

    /** 汇总分析师报告为文本摘要 */
    protected String summarizeReports(AnalysisContext context) {
        Map<String, AgentReport> reports = context.agentReports();
        if (reports == null || reports.isEmpty()) return "暂无分析师报告";
        return reports.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue().summary())
                .collect(Collectors.joining("\n"));
    }

    /** 调用 LLM 完成辩论 */
    protected String chat(String systemPrompt, String userPrompt) {
        return llmService.chat(providerType, baseUrl, apiKey, modelId, systemPrompt, userPrompt).content();
    }
}
