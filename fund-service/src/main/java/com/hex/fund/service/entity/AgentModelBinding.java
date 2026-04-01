package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

/**
 * AI Agent 与模型的绑定配置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_AGENT_MODEL_BINDING")
public class AgentModelBinding extends BaseEntity {

    @TableField("AGENT_ID")
    private String agentId;
    @TableField("THINK_LEVEL")
    private String thinkLevel;
    @TableField("PROVIDER_CODE")
    private String providerCode;
    @TableField("MODEL_ID")
    private String modelId;
    @TableField("ENABLED")
    private Integer enabled;
}
