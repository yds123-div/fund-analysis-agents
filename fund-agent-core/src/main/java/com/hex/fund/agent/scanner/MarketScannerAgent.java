package com.hex.fund.agent.scanner;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/**
 * 市场环境扫描师 — 判断当前市场温度（危机/低迷/震荡/正常/过热）。
 */
@Slf4j
public class MarketScannerAgent {

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

    /** 扫描市场环境，返回 JSON 格式的市场温度评估 */
    public String scan(String marketData) {
        String systemPrompt = promptLoader.load("decision/market-scanner-system");
        String userPrompt = promptLoader.load("decision/market-scanner-user",
                LocalDate.now().toString(), marketData != null ? marketData : "暂无详细数据");
        log.info("[市场扫描] 开始评估市场温度");
        String result = llmService.chat(providerType, baseUrl, apiKey, modelId,
                systemPrompt, userPrompt).content();
        log.info("[市场扫描] 市场温度评估完成");
        return result;
    }
}
