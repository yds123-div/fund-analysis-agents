package com.hex.fund.agent.core;

import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.AnalysisContext;

/**
 * 分析 Agent 基础接口 — 每个 Agent 聚焦于一个特定的分析维度。
 */
public interface AnalysisAgent {

    String getAgentId();

    String getAgentRole();

    AgentReport analyze(AnalysisContext context);
}
