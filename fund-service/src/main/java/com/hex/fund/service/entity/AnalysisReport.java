package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 多 Agent 分析报告实体，聚合各分析师 Agent 的结果。
 * 注：该实体无 UPDATED_AT 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "TM_ANALYSIS_REPORT", autoResultMap = true)
public class AnalysisReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("USER_ID")
    private Long userId;
    @TableField("EXECUTION_ID")
    private Long executionId;
    @TableField("BATCH_NO")
    private String batchNo;
    @TableField("REPORT_VERSION")
    private Integer reportVersion;
    @TableField("INPUT_SNAPSHOT_ID")
    private String inputSnapshotId;
    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("REPORT_TYPE")
    private String reportType;
    @TableField("REPORT_DATE")
    private LocalDate reportDate;
    @TableField("OVERALL_RATING")
    private String overallRating;
    @TableField("OVERALL_SCORE")
    private BigDecimal overallScore;
    @TableField("CONFIDENCE_LEVEL")
    private String confidenceLevel;
    @TableField("RECOMMENDATION")
    private String recommendation;
    @TableField("POSITION_SUGGESTION")
    private BigDecimal positionSuggestion;
    @TableField("TIME_HORIZON")
    private String timeHorizon;
    @TableField("ADD_POSITION_TRIGGER")
    private String addPositionTrigger;
    @TableField("REDUCE_POSITION_TRIGGER")
    private String reducePositionTrigger;
    @TableField("INVALIDATION_CONDITION")
    private String invalidationCondition;
    @TableField(value = "FUND_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String fundAnalystResult;
    @TableField(value = "TECHNICAL_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String technicalAnalystResult;
    @TableField(value = "INDUSTRY_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String industryAnalystResult;
    @TableField(value = "SENTIMENT_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String sentimentAnalystResult;
    @TableField(value = "NEWS_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String newsAnalystResult;
    @TableField(value = "MANAGER_ANALYST_RESULT", typeHandler = JacksonTypeHandler.class)
    private String managerAnalystResult;
    @TableField(value = "BULLISH_RESEARCHER_RESULT", typeHandler = JacksonTypeHandler.class)
    private String bullishResearcherResult;
    @TableField(value = "BEARISH_RESEARCHER_RESULT", typeHandler = JacksonTypeHandler.class)
    private String bearishResearcherResult;
    @TableField(value = "DEBATE_SUMMARY", typeHandler = JacksonTypeHandler.class)
    private String debateSummary;
    @TableField(value = "TRADER_RESULT", typeHandler = JacksonTypeHandler.class)
    private String traderResult;
    @TableField(value = "RISK_MANAGER_RESULT", typeHandler = JacksonTypeHandler.class)
    private String riskManagerResult;
    @TableField(value = "PORTFOLIO_ADVISOR_RESULT", typeHandler = JacksonTypeHandler.class)
    private String portfolioAdvisorResult;
    @TableField("SUMMARY")
    private String summary;
    @TableField("HTML_CONTENT")
    private String htmlContent;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
