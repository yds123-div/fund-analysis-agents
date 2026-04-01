package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金基本信息实体，包含净值、业绩指标和风险指标。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_FUND_INFO")
public class FundInfo extends BaseEntity {

    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("FUND_NAME")
    private String fundName;
    @TableField("FUND_TYPE")
    private String fundType;
    @TableField("MANAGEMENT_COMPANY")
    private String managementCompany;
    @TableField("FUND_MANAGER")
    private String fundManager;
    @TableField("ESTABLISH_DATE")
    private LocalDate establishDate;
    @TableField("FUND_SCALE")
    private BigDecimal fundScale;
    @TableField("NAV")
    private BigDecimal nav;
    @TableField("ACCUMULATED_NAV")
    private BigDecimal accumulatedNav;
    @TableField("DAY_GROWTH_RATE")
    private BigDecimal dayGrowthRate;
    @TableField("SHARPE_RATIO")
    private BigDecimal sharpeRatio;
    @TableField("MAX_DRAWDOWN")
    private BigDecimal maxDrawdown;
    @TableField("VOLATILITY")
    private BigDecimal volatility;
    @TableField("DATA_UPDATE_TIME")
    private LocalDateTime dataUpdateTime;
}
