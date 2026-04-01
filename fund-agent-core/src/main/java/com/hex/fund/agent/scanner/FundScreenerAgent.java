package com.hex.fund.agent.scanner;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/**
 * 智能选基师 — 基于市场环境和用户偏好筛选推荐基金。
 */
@Slf4j
public class FundScreenerAgent {

    private LlmService llmService;
    private PromptLoader promptLoader;
    private String providerType, baseUrl, apiKey, modelId;

    public void configure(LlmService llmService, PromptLoader promptLoader,
                          String providerType, String baseUrl, String apiKey, String modelId) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    /** 基于市场温度和用户偏好推荐基金 */
    public String screen(String marketTemperature, String riskPreference, String marketData) {
        String systemPrompt = promptLoader.load("decision/fund-screener-system");
        String userPrompt = promptLoader.load("decision/fund-screener-user",
                LocalDate.now().toString(), marketTemperature,
                riskPreference != null ? riskPreference : "中等风险",
                marketData != null ? marketData : "暂无详细数据");
        log.info("[智能选基] 开始筛选推荐基金");
        String result = llmService.chat(providerType, baseUrl, apiKey, modelId,
                systemPrompt, userPrompt).content();
        log.info("[智能选基] 基金筛选完成");
        return result;
    }
}
