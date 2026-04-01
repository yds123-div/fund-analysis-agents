package com.hex.fund.agent.decision;

import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

/**
 * 报告生成 Agent — 综合所有分析结果生成最终结构化投资报告。
 */
@Slf4j
public class ReportGeneratorAgent extends AbstractDecisionAgent {

    public String generate(AnalysisContext context, DebateRecord debate,
                           String traderAdvice, String riskAssessment) {
        String systemPrompt = promptLoader.load("decision/report-generator-system");
        String reports = context.agentReports() != null
                ? context.agentReports().entrySet().stream()
                .map(e -> "【" + e.getValue().agentRole() + "】" + e.getValue().summary())
                .collect(Collectors.joining("\n"))
                : "暂无";
        String userPrompt = promptLoader.load("decision/report-generator-user",
                context.fundName(), context.fundCode(), context.analysisDate(),
                reports, debate.consensus(), debate.divergence(), debate.finalVerdict(),
                traderAdvice, riskAssessment);
        return chat(systemPrompt, userPrompt);
    }
}
