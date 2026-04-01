package com.hex.fund.common.util;

import java.math.BigDecimal;

/**
 * 通用类型转换工具类。
 */
public final class ConversionUtil {

    private ConversionUtil() {
    }

    /**
     * 将任意对象安全转换为 BigDecimal，转换失败返回 null。
     */
    public static BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将任意对象安全转换为 double，转换失败返回 0。
     */
    public static double toDouble(Object val) {
        return val instanceof Number n ? n.doubleValue() : 0;
    }
}
