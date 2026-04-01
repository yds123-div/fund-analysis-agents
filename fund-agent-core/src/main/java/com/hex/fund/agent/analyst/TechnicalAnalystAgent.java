package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 技术分析师 — 净值趋势、回撤、波动率、夏普比率分析。
 */
public class TechnicalAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "technical_analyst"; }

    @Override
    public String getAgentRole() { return "技术分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/technical-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        var meta = context.metadata();
        String fundBasic = meta != null ? String.valueOf(meta.getOrDefault("fundBasic", "暂无")) : "暂无";
        String summary = meta != null ? String.valueOf(meta.getOrDefault("performanceSummary", "暂无")) : "暂无";
        String estimate = meta != null ? String.valueOf(meta.getOrDefault("realTimeEstimate", "非交易时段")) : "非交易时段";
        String navHistory = meta != null ? String.valueOf(meta.getOrDefault("navHistory", "暂无")) : "暂无";
        return promptLoader.load("analyst/technical-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                fundBasic, summary, estimate, navHistory);
    }
}
