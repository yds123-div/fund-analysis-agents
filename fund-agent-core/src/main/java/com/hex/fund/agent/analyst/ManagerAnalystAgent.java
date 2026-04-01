package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 基金经理分析师 — 经验、业绩记录、投资风格、任期稳定性分析。
 */
public class ManagerAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "manager_analyst"; }

    @Override
    public String getAgentRole() { return "基金经理分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/manager-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        var meta = context.metadata();
        return promptLoader.load("analyst/manager-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                meta != null ? meta.getOrDefault("managerData", "暂无详细数据") : "暂无详细数据");
    }
}
