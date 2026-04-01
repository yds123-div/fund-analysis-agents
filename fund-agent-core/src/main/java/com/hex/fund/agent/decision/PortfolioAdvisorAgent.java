package com.hex.fund.agent.decision;

import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * 组合顾问 Agent — 从组合层面给出再平衡建议和资产配置优化。
 */
@Slf4j
public class PortfolioAdvisorAgent extends AbstractDecisionAgent {

    public String advise(AnalysisContext context, DebateRecord debate,
                         String traderAdvice, String riskAssessment) {
        String systemPrompt = promptLoader.load("decision/portfolio-advisor-system");
        String userPrompt = promptLoader.load("decision/portfolio-advisor-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                debate != null ? debate.finalVerdict() : "", traderAdvice, riskAssessment);
        return chat(systemPrompt, userPrompt);
    }
}
