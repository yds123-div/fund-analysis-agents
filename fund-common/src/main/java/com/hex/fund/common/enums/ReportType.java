package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Analysis report type.
 */
@Getter
@AllArgsConstructor
public enum ReportType {

    INTRADAY("INTRADAY", "盘中监控"),
    AFTER_MARKET("AFTER_MARKET", "盘后分析"),
    DAILY("DAILY", "每日报告"),
    WEEKLY("WEEKLY", "周报"),
    MONTHLY("MONTHLY", "月报");

    private final String code;
    private final String desc;
}
