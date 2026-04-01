package com.hex.fund.agent.decision;

import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * 风控评估 Agent — 评估风险敞口并提供风险评估报告。
 */
@Slf4j
public class RiskManagerAgent extends AbstractDecisionAgent {

    public String assess(AnalysisContext context, DebateRecord debate, String traderAdvice) {
        String systemPrompt = promptLoader.load("decision/risk-manager-system");
        String userPrompt = promptLoader.load("decision/risk-manager-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                debate.finalVerdict(), traderAdvice);
        return chat(systemPrompt, userPrompt);
    }
}
