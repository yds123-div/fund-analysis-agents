package com.hex.fund.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Business error codes.
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    SYSTEM_ERROR(10000, "system error"),
    PARAM_INVALID(10001, "invalid parameter"),
    DATA_NOT_FOUND(10002, "data not found"),
    DATA_DUPLICATE(10003, "duplicate data"),
    // Fund
    FUND_NOT_FOUND(20001, "fund not found"),
    FUND_DATA_FETCH_FAILED(20002, "failed to fetch fund data"),
    // Portfolio
    PORTFOLIO_DUPLICATE(30001, "fund already in portfolio"),
    // Analysis
    ANALYSIS_IN_PROGRESS(40001, "analysis already in progress"),
    ANALYSIS_NO_LLM_KEY(40002, "no LLM API key configured"),
    // DataSource
    DATASOURCE_UNAVAILABLE(50001, "data source unavailable"),
    DATASOURCE_L1_INSUFFICIENT(50002, "L1 data source insufficient for recommendation"),
    // Auth
    AUTH_BAD_CREDENTIALS(60001, "invalid username or password"),
    AUTH_ACCOUNT_LOCKED(60002, "account locked, try again later"),
    AUTH_ACCOUNT_DISABLED(60003, "account disabled"),
    AUTH_TOKEN_INVALID(60004, "invalid or expired token"),
    AUTH_ACCESS_DENIED(60005, "access denied");

    private final int code;
    private final String message;
}
