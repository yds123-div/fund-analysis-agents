package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 新闻分析师 — 宏观政策、行业新闻、基金公告分析。
 */
public class NewsAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "news_analyst"; }

    @Override
    public String getAgentRole() { return "新闻分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/news-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        return promptLoader.load("analyst/news-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                context.metadata() != null ? context.metadata().toString() : "暂无详细数据");
    }
}
