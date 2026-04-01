package com.hex.fund.agent.model;

/**
 * LLM 调用追踪记录 — 用于审计和性能监控。
 */
public record ModelTrace(
        String provider,
        String modelId,
        String promptTemplateVer,
        int inputTokens,
        int outputTokens,
        long latencyMs
) {
}
