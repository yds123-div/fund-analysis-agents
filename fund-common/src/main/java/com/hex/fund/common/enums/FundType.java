package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Fund type classification based on CSRC standards.
 */
@Getter
@AllArgsConstructor
public enum FundType {

    STOCK("STOCK", "股票型"),
    HYBRID_EQUITY("HYBRID_EQUITY", "偏股混合型"),
    HYBRID_BOND("HYBRID_BOND", "偏债混合型"),
    HYBRID_BALANCED("HYBRID_BALANCED", "平衡混合型"),
    HYBRID_FLEXIBLE("HYBRID_FLEXIBLE", "灵活配置型"),
    INDEX("INDEX", "指数型"),
    ETF("ETF", "ETF"),
    BOND("BOND", "债券型"),
    MONEY("MONEY", "货币型"),
    QDII("QDII", "QDII"),
    FOF("FOF", "FOF"),
    REITS("REITS", "REITs"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;
}
