package com.hex.fund.agent.debate;

import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import com.hex.fund.agent.model.DebateRecord.DebateRound;
import com.hex.fund.agent.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 辩论协调器 — 协调看多/看空研究员进行多轮辩论并综合裁决。
 */
@Slf4j
public class DebateCoordinator {

    private static final Map<String, BiConsumer<String, Map<String, String>>> VERDICT_PARSERS = Map.of(
            "共识", (val, result) -> result.put("consensus", val),
            "分歧", (val, result) -> result.put("divergence", val),
            "综合判断", (val, result) -> result.put("finalVerdict", val)
    );

    private final BullishResearcherAgent bullish = new BullishResearcherAgent();
    private final BearishResearcherAgent bearish = new BearishResearcherAgent();
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
        bullish.configure(llmService, promptLoader, providerType, baseUrl, apiKey, modelId);
        bearish.configure(llmService, promptLoader, providerType, baseUrl, apiKey, modelId);
    }

    /** 执行多轮辩论并返回辩论记录 */
    public DebateRecord debate(AnalysisContext context, int maxRounds) {
        log.info("开始辩论: 基金={}, 最大轮数={}", context.fundCode(), maxRounds);
        List<DebateRound> rounds = executeRounds(context, maxRounds);
        String verdict = synthesize(context, rounds);
        log.info("辩论结束: 基金={}", context.fundCode());
        return parseVerdict(rounds, verdict);
    }

    private List<DebateRound> executeRounds(AnalysisContext context, int maxRounds) {
        List<DebateRound> rounds = new ArrayList<>();
        String lastBullish = null, lastBearish = null;
        for (int i = 1; i <= maxRounds; i++) {
            log.info("辩论第 {}/{} 轮", i, maxRounds);
            lastBullish = bullish.argue(context, lastBearish);
            lastBearish = bearish.argue(context, lastBullish);
            rounds.add(new DebateRound(i, lastBullish, lastBearish, null));
        }
        return rounds;
    }

    private String synthesize(AnalysisContext context, List<DebateRound> rounds) {
        StringBuilder history = new StringBuilder();
        for (DebateRound r : rounds) {
            history.append(String.format("=== 第%d轮 ===\n看多：%s\n看空：%s\n\n",
                    r.roundNumber(), r.bullishArgument(), r.bearishArgument()));
        }
        String systemPrompt = promptLoader.load("debate/debate-coordinator-system");
        String userPrompt = promptLoader.load("debate/debate-coordinator-user",
                context.fundName(), context.fundCode(), history.toString());
        return llmService.chat(providerType, baseUrl, apiKey, modelId, systemPrompt, userPrompt).content();
    }

    /** 使用 Map 策略解析裁决文本中的共识、分歧、综合判断 */
    private DebateRecord parseVerdict(List<DebateRound> rounds, String verdict) {
        Map<String, String> result = new HashMap<>(Map.of(
                "consensus", "", "divergence", "", "finalVerdict", verdict));
        for (String line : verdict.split("\n")) {
            String trimmed = line.trim();
            VERDICT_PARSERS.forEach((prefix, consumer) -> {
                if (trimmed.startsWith(prefix + "：") || trimmed.startsWith(prefix + ":")) {
                    consumer.accept(trimmed.substring(prefix.length() + 1).trim(), result);
                }
            });
        }
        return new DebateRecord(rounds, result.get("consensus"),
                result.get("divergence"), result.get("finalVerdict"));
    }
}
