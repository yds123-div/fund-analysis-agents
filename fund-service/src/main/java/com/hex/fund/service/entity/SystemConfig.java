package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

/**
 * 系统级键值配置实体，按功能分组。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_SYSTEM_CONFIG")
public class SystemConfig extends BaseEntity {

    @TableField("CONFIG_GROUP")
    private String configGroup;
    @TableField("CONFIG_KEY")
    private String configKey;
    @TableField("CONFIG_VALUE")
    private String configValue;
    @TableField("CONFIG_TYPE")
    private String configType;
    @TableField("DESCRIPTION")
    private String description;
}
