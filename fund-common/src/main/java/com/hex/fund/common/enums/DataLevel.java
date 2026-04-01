package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Data source reliability level for recommendation engine.
 */
@Getter
@AllArgsConstructor
public enum DataLevel {

    L1("L1", "推荐核心-需快照留存和交叉验证"),
    L2("L2", "分析辅助-允许单源和缓存兜底"),
    L3("L3", "展示参考-允许降级和缺失");

    private final String code;
    private final String desc;
}
