package com.hex.fund.agent.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 单个分析 Agent 产出的报告。
 */
public record AgentReport(
        String agentId,
        String agentRole,
        String summary,
        List<String> keyFindings,
        BigDecimal confidenceScore,
        String detailedAnalysis,
        Map<String, Object> structuredData,
        List<EvidenceItem> evidences,
        ModelTrace modelTrace
) {
}
