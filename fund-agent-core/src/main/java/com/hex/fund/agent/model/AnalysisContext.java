package com.hex.fund.agent.model;

import com.hex.fund.common.enums.ReportType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 分析管线共享上下文 — 贯穿整个分析流程。
 */
public record AnalysisContext(
        String fundCode,
        String fundName,
        LocalDate analysisDate,
        ReportType type,
        String batchNo,
        Map<String, AgentReport> agentReports,
        List<EvidenceItem> evidenceChain,
        Map<String, Object> metadata
) {
}
