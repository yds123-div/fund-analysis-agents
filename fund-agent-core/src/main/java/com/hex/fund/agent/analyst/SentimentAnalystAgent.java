package com.hex.fund.agent.analyst;

import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 市场情绪分析师 — 恐贪指数、资金流向、申赎比分析。
 */
public class SentimentAnalystAgent extends AbstractAnalysisAgent {

    @Override
    public String getAgentId() { return "sentiment_analyst"; }

    @Override
    public String getAgentRole() { return "市场情绪分析师"; }

    @Override
    protected String getSystemPrompt() {
        return promptLoader.load("analyst/sentiment-analyst-system") + outputFormatInstruction();
    }

    @Override
    protected String buildUserPrompt(AnalysisContext context) {
        return promptLoader.load("analyst/sentiment-analyst-user",
                context.fundCode(), context.fundName(), context.analysisDate(),
                context.metadata() != null ? context.metadata().toString() : "暂无详细数据");
    }
}
