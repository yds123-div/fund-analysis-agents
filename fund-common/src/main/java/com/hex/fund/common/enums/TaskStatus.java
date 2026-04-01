package com.hex.fund.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Task execution status.
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    TIMEOUT("TIMEOUT", "超时"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;
}
