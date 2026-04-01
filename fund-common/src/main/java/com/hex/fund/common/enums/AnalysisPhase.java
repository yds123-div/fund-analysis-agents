package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分析流程阶段定义，每个阶段对应 Graph 中的一个 Node。
 */
@Getter
@AllArgsConstructor
public enum AnalysisPhase {

    DATA_COLLECTION(10, "数据采集"),
    PARALLEL_ANALYSIS(30, "并行分析"),
    DEBATE(50, "多空辩论"),
    TRADER(70, "交易建议"),
    RISK_MANAGER(85, "风控评估"),
    REPORT_GENERATOR(95, "报告生成"),
    COMPLETED(100, "分析完成");

    private final int progress;
    private final String desc;
}
