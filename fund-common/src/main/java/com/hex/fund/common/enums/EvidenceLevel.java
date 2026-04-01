package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Evidence credibility level for analysis traceability.
 */
@Getter
@AllArgsConstructor
public enum EvidenceLevel {

    FACT("FACT", "已披露事实"),
    ESTIMATE("ESTIMATE", "实时估算"),
    INFERENCE("INFERENCE", "AI推断");

    private final String code;
    private final String desc;
}
