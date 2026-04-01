package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户自选基金列表实体。
 * 注：该实体无 UPDATED_AT 字段，仅有 CREATED_AT。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("TM_WATCH_LIST")
public class WatchList {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("USER_ID")
    private Long userId;
    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("PRIORITY")
    private Integer priority;
    @TableField("NOTES")
    private String notes;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
