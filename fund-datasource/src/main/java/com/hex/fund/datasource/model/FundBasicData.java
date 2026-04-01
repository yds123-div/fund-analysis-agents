package com.hex.fund.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fund basic information from data source.
 */
@Data
@Builder
public class FundBasicData {

    private String fundCode;
    private String fundName;
    private String fundType;
    private String managementCompany;
    private String fundManager;
    private LocalDate establishDate;
    private BigDecimal fundScale;
    private BigDecimal nav;
    private BigDecimal accumulatedNav;
    private BigDecimal dayGrowthRate;
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private BigDecimal volatility;
    private LocalDateTime dataUpdateTime;
    private String source;
}
