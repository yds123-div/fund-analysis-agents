package com.hex.fund.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fund NAV (Net Asset Value) data point.
 */
@Data
@Builder
public class NavData {

    private String fundCode;
    private LocalDate navDate;
    private BigDecimal unitNav;
    private BigDecimal accumulatedNav;
    private BigDecimal dayGrowthRate;
    private String source;
}
