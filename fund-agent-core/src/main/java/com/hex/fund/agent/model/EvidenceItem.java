package com.hex.fund.agent.model;

import com.hex.fund.common.enums.EvidenceLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证据链条目 — 追踪分析中每条数据的来源和时效性。
 */
public record EvidenceItem(
        String sourceId,
        String dataType,
        EvidenceLevel level,
        LocalDateTime fetchTime,
        LocalDate dataAsOfDate,
        String snapshotId
) {
}
