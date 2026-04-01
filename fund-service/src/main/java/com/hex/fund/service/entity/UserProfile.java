package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.math.BigDecimal;

/**
 * 用户投资画像实体，记录风险偏好和投资约束。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "TM_USER_PROFILE", autoResultMap = true)
public class UserProfile extends BaseEntity {

    @TableField("USER_ID")
    private Long userId;
    @TableField("RISK_LEVEL")
    private String riskLevel;
    @TableField("INVESTMENT_HORIZON")
    private String investmentHorizon;
    @TableField("MAX_DRAWDOWN_TOLERANCE")
    private BigDecimal maxDrawdownTolerance;
    @TableField(value = "PREFERRED_INDUSTRIES", typeHandler = JacksonTypeHandler.class)
    private String preferredIndustries;
    @TableField(value = "AVOIDED_INDUSTRIES", typeHandler = JacksonTypeHandler.class)
    private String avoidedIndustries;
    @TableField("FUND_SCALE_PREFERENCE")
    private String fundScalePreference;
    @TableField("TURNOVER_TOLERANCE")
    private String turnoverTolerance;
    @TableField("ACCEPT_NEW_FUND")
    private Integer acceptNewFund;
    @TableField("CONCENTRATION_PREFERENCE")
    private String concentrationPreference;
    @TableField("COMPLETED")
    private Integer completed;
}
