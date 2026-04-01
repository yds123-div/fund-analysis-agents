package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 基金分析师 — 持仓分析与基本面评估。
 */
public class FundAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "fund_analyst"; }

    @Override
    public String getAgentRole() { return "基金分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/fund-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        var meta = context.metadata();
        String summary = meta != null ? String.valueOf(meta.getOrDefault("performanceSummary", "暂无")) : "暂无";
        String allData = meta != null ? meta.toString() : "暂无详细数据";
        return promptLoader.load("analyst/fund-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                summary, allData);
    }
}
