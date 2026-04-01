package com.hex.fund.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Fund holding position from quarterly report.
 */
@Data
@Builder
public class HoldingData {

    private String fundCode;
    private String stockCode;
    private String stockName;
    private BigDecimal holdingPercent;
    private BigDecimal holdingAmount;
    private BigDecimal holdingValue;
    private String reportDate;
    private String source;
}
