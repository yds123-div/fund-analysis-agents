package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI 提供商配置实体，存储 API 凭证和连通性状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_AI_PROVIDER_CONFIG")
public class AiProviderConfig extends BaseEntity {

    @TableField("PROVIDER_CODE")
    private String providerCode;
    @TableField("PROVIDER_NAME")
    private String providerName;
    /** "dashscope" 为 DashScope 原生，"openai" 为 OpenAI 兼容（DeepSeek、GLM 等） */
    @TableField("PROVIDER_TYPE")
    private String providerType;
    @TableField("API_KEY_ENCRYPTED")
    private String apiKeyEncrypted;
    @TableField("BASE_URL")
    private String baseUrl;
    @TableField("ENABLED")
    private Integer enabled;
    @TableField("CONNECTIVITY_STATUS")
    private String connectivityStatus;
    @TableField("LAST_CHECK_TIME")
    private LocalDateTime lastCheckTime;
}
