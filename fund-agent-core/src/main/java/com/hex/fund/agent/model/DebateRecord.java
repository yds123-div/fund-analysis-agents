package com.hex.fund.agent.model;

import java.util.List;

/**
 * 多轮辩论记录 — 包含各轮辩论内容及最终裁决。
 */
public record DebateRecord(
        List<DebateRound> rounds,
        String consensus,
        String divergence,
        String finalVerdict
) {

    public record DebateRound(
            int roundNumber,
            String bullishArgument,
            String bearishArgument,
            String roundSummary
    ) {
    }
}
