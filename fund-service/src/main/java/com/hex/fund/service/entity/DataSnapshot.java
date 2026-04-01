package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 不可变数据快照实体，用于分析输入的可复现性。
 * 注：该实体无 UPDATED_AT 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "TM_DATA_SNAPSHOT", autoResultMap = true)
public class DataSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("SNAPSHOT_ID")
    private String snapshotId;
    @TableField("SNAPSHOT_TYPE")
    private String snapshotType;
    @TableField("SNAPSHOT_DATE")
    private LocalDate snapshotDate;
    @TableField(value = "DATA_CONTENT", typeHandler = JacksonTypeHandler.class)
    private String dataContent;
    @TableField("SOURCE")
    private String source;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
