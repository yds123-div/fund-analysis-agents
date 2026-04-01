package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 基金推荐结果实体，包含市场分析和组合建议。
 * 注：该实体无 UPDATED_AT 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "TM_FUND_RECOMMENDATION", autoResultMap = true)
public class FundRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("USER_ID")
    private Long userId;
    @TableField("BATCH_NO")
    private String batchNo;
    @TableField("MARKET_TEMPERATURE")
    private String marketTemperature;
    @TableField("MARKET_SCORE")
    private Integer marketScore;
    @TableField(value = "MARKET_ANALYSIS", typeHandler = JacksonTypeHandler.class)
    private String marketAnalysis;
    @TableField(value = "RECOMMENDED_FUNDS", typeHandler = JacksonTypeHandler.class)
    private String recommendedFunds;
    @TableField(value = "PORTFOLIO_CHECK_RESULT", typeHandler = JacksonTypeHandler.class)
    private String portfolioCheckResult;
    @TableField("REPORT_CONTENT")
    private String reportContent;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
