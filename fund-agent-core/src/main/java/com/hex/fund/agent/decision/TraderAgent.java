package com.hex.fund.agent.decision;

import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

/**
 * 交易建议 Agent — 综合所有分析结果生成可操作的交易建议。
 */
@Slf4j
public class TraderAgent extends AbstractDecisionAgent {

    public String advise(AnalysisContext context, DebateRecord debate) {
        String systemPrompt = promptLoader.load("decision/trader-system");
        String reports = context.agentReports() != null
                ? context.agentReports().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue().summary())
                .collect(Collectors.joining("\n"))
                : "暂无";
        String userPrompt = promptLoader.load("decision/trader-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                reports, debate.consensus(), debate.divergence(), debate.finalVerdict());
        return chat(systemPrompt, userPrompt);
    }
}
