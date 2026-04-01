package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户账户实体，用于认证与授权。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_USER")
public class User extends BaseEntity {

    @TableField("USERNAME")
    private String username;
    @TableField("NICKNAME")
    private String nickname;
    @TableField("EMAIL")
    private String email;
    @TableField("PHONE")
    private String phone;
    @TableField("PASSWORD_HASH")
    private String passwordHash;
    @TableField("ROLE")
    private String role;
    @TableField("STATUS")
    private Integer status;
    @TableField("LAST_LOGIN_TIME")
    private LocalDateTime lastLoginTime;
    @TableField("LOGIN_FAIL_COUNT")
    private Integer loginFailCount;
    @TableField("LOCK_UNTIL")
    private LocalDateTime lockUntil;
}
