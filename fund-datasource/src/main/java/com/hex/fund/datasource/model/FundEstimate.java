package com.hex.fund.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Real-time fund NAV estimate (intraday).
 */
@Data
@Builder
public class FundEstimate {

    private String fundCode;
    private String fundName;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private LocalDateTime estimateTime;
    private String source;
}
