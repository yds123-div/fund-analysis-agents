package com.hex.fund.service.entity.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础实体，包含所有领域实体的公共审计字段。
 */
@Data
public class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(value = "CREATED_AT", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(value = "UPDATED_AT", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
