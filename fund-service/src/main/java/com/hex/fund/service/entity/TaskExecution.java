package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 任务执行记录实体，跟踪状态、时间和重试信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_TASK_EXECUTION")
public class TaskExecution extends BaseEntity {

    @TableField("TASK_ID")
    private Long taskId;
    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("FUND_NAME")
    private String fundName;
    @TableField("BATCH_NO")
    private String batchNo;
    @TableField("STATUS")
    private String status;
    @TableField("PROGRESS")
    private Integer progress;
    @TableField("CURRENT_STAGE")
    private String currentStage;
    @TableField("TRIGGER_TYPE")
    private String triggerType;
    @TableField("SCHEDULED_TIME")
    private LocalDateTime scheduledTime;
    @TableField("START_TIME")
    private LocalDateTime startTime;
    @TableField("END_TIME")
    private LocalDateTime endTime;
    @TableField("INPUT_SNAPSHOT_ID")
    private String inputSnapshotId;
    @TableField("ERROR_MESSAGE")
    private String errorMessage;
    @TableField("RETRY_COUNT")
    private Integer retryCount;
    @TableField("MAX_RETRY")
    private Integer maxRetry;
}
