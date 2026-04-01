package com.hex.fund.agent.decision;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * 决策类 Agent 基类 — 封装 configure 和 LLM 调用等公共逻辑，
 * 子类包括 TraderAgent、RiskManagerAgent、PortfolioAdvisorAgent、ReportGeneratorAgent。
 */
@Slf4j
public abstract class AbstractDecisionAgent {

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

    /** 调用 LLM 完成对话 */
    protected String chat(String systemPrompt, String userPrompt) {
        return llmService.chat(providerType, baseUrl, apiKey, modelId, systemPrompt, userPrompt).content();
    }
}
