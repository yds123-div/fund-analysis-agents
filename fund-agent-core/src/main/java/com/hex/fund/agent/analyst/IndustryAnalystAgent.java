package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 行业分析师 — 行业景气度、政策影响、板块轮动信号分析。
 */
public class IndustryAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "industry_analyst"; }

    @Override
    public String getAgentRole() { return "行业分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/industry-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        return promptLoader.load("analyst/industry-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                context.metadata() != null ? context.metadata().toString() : "暂无详细数据");
    }
}
