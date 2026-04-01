package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 外部数据源配置实体，用于基金数据获取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_DATASOURCE_CONFIG")
public class DatasourceConfig extends BaseEntity {

    @TableField("SOURCE_CODE")
    private String sourceCode;
    @TableField("SOURCE_NAME")
    private String sourceName;
    @TableField("SOURCE_TYPE")
    private String sourceType;
    @TableField("BASE_URL")
    private String baseUrl;
    @TableField("API_KEY_ENCRYPTED")
    private String apiKeyEncrypted;
    @TableField("DATA_LEVEL")
    private String dataLevel;
    @TableField("ENABLED")
    private Integer enabled;
    @TableField("PRIORITY")
    private Integer priority;
    @TableField("CONNECTIVITY_STATUS")
    private String connectivityStatus;
    @TableField("LAST_CHECK_TIME")
    private LocalDateTime lastCheckTime;
}
