package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

/**
 * 定时分析任务配置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_ANALYSIS_TASK")
public class AnalysisTask extends BaseEntity {

    @TableField("USER_ID")
    private Long userId;
    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("TASK_TYPE")
    private String taskType;
    @TableField("REPORT_TYPE")
    private String reportType;
    @TableField("CRON_EXPRESSION")
    private String cronExpression;
    @TableField("ENABLED")
    private Boolean enabled;
    @TableField("DESCRIPTION")
    private String description;
    @TableField("NOTIFICATION_CHANNELS")
    private String notificationChannels;
    @TableField("NOTIFICATION_TARGETS")
    private String notificationTargets;
    @TableField("TIMEOUT_MINUTES")
    private Integer timeoutMinutes;
}
