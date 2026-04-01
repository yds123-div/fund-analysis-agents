package com.hex.fund.datasource.util;

import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.FundBasicData;
import lombok.extern.slf4j.Slf4j;

/**
 * 基金通用工具类。
 */
@Slf4j
public final class FundUtil {

    private FundUtil() {
    }

    /**
     * 根据基金代码解析基金名称，解析失败返回空字符串。
     */
    public static String resolveFundName(DataSourceManager dataSourceManager, String fundCode) {
        try {
            FundBasicData basic = dataSourceManager.getAggregatedFundBasic(fundCode);
            return basic != null && basic.getFundName() != null ? basic.getFundName() : "";
        } catch (Exception e) {
            log.debug("解析基金名称失败: fundCode={}", fundCode, e);
            return "";
        }
    }
}
