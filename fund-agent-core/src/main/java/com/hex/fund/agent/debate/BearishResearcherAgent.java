package com.hex.fund.agent.debate;

import com.hex.fund.agent.model.AnalysisContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 看空研究员 — 质疑投资论点并识别风险。
 */
@Slf4j
public class BearishResearcherAgent extends AbstractDebateResearcherAgent {

    @Override
    public String argue(AnalysisContext context, String previousBullishArgument) {
        String systemPrompt = promptLoader.load("debate/bearish-researcher-system");
        String prevArg = previousBullishArgument != null
                ? "看多研究员的观点：" + previousBullishArgument : "这是第一轮辩论。";
        String userPrompt = promptLoader.load("debate/bearish-researcher-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                summarizeReports(context), prevArg);
        return chat(systemPrompt, userPrompt);
    }
}
