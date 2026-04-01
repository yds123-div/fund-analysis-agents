package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知推送记录实体，跟踪发送状态和错误详情。
 * 注：该实体无 UPDATED_AT 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("TM_NOTIFICATION")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("USER_ID")
    private Long userId;
    @TableField("REPORT_ID")
    private Long reportId;
    @TableField("CHANNEL")
    private String channel;
    @TableField("TITLE")
    private String title;
    @TableField("CONTENT")
    private String content;
    @TableField("STATUS")
    private String status;
    @TableField("SENT_TIME")
    private LocalDateTime sentTime;
    @TableField("ERROR_MESSAGE")
    private String errorMessage;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
