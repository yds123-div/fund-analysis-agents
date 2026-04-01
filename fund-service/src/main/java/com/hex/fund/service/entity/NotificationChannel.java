package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

/**
 * 用户通知渠道配置实体（如邮件、Bark 等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "TM_NOTIFICATION_CHANNEL", autoResultMap = true)
public class NotificationChannel extends BaseEntity {

    @TableField("USER_ID")
    private Long userId;
    @TableField("CHANNEL_TYPE")
    private String channelType;
    @TableField(value = "CONFIG_JSON", typeHandler = JacksonTypeHandler.class)
    private String configJson;
    @TableField("ENABLED")
    private Integer enabled;
}
