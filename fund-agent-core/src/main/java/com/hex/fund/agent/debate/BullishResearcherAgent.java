package com.hex.fund.agent.debate;

import com.hex.fund.agent.model.AnalysisContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 看多研究员 — 论证基金的投资价值。
 */
@Slf4j
public class BullishResearcherAgent extends AbstractDebateResearcherAgent {

    @Override
    public String argue(AnalysisContext context, String previousBearishArgument) {
        String systemPrompt = promptLoader.load("debate/bullish-researcher-system");
        String prevArg = previousBearishArgument != null
                ? "看空研究员的观点：" + previousBearishArgument : "这是第一轮辩论。";
        String userPrompt = promptLoader.load("debate/bullish-researcher-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                summarizeReports(context), prevArg);
        return chat(systemPrompt, userPrompt);
    }
}
