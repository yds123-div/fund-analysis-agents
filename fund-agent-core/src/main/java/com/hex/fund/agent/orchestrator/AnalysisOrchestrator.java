package com.hex.fund.agent.orchestrator;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.hex.fund.agent.graph.AnalysisGraphBuilder;
import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.DebateRecord;
import com.hex.fund.common.enums.ReportType;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 分析流程编排器 — 基于 Spring AI Alibaba Graph 驱动完整分析管线。
 * 流程: START → data_collection → parallel_analysis → debate → trader → risk_manager → portfolio_advisor → report_generator → END
 */
@Slf4j
@Component
public class AnalysisOrchestrator {

    /** 根 observation 名 - 标识一次完整基金分析（在 Opik 看板作为 trace 根 span）。 */
    static final String ROOT_OBSERVATION_NAME = "基金分析";

    private final AnalysisGraphBuilder graphBuilder;
    private final ObservationRegistry observationRegistry;
    private int debateMaxRounds = 3;

    public AnalysisOrchestrator(AnalysisGraphBuilder graphBuilder, ObservationRegistry observationRegistry) {
        this.graphBuilder = graphBuilder;
        this.observationRegistry = observationRegistry;
    }

    public void configure(int debateMaxRounds) {
        this.debateMaxRounds = debateMaxRounds;
    }

    /** 执行完整的基金分析管线 */
    @SuppressWarnings("unchecked")
    public AnalysisResult analyze(String fundCode, String fundName, ReportType reportType,
                                  String providerType, String baseUrl, String apiKey, String modelId) {
        String batchNo = generateBatchNo();
        log.info("开始图编排分析: 基金={}, 批次={}", fundCode, batchNo);
        Map<String, Object> input = buildGraphInput(fundCode, fundName, batchNo,
                providerType, baseUrl, apiKey, modelId);
        OverAllState finalState = executeGraphTraced(input, fundCode, fundName, batchNo, providerType, modelId);
        AnalysisResult result = extractResult(finalState, batchNo);
        log.info("图编排分析完成: 基金={}, 批次={}, Agent数={}",
                fundCode, batchNo, result.agentReports().size());
        return result;
    }

    private String generateBatchNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Map<String, Object> buildGraphInput(String fundCode, String fundName, String batchNo,
                                                 String providerType, String baseUrl, String apiKey, String modelId) {
        Map<String, Object> input = new HashMap<>();
        input.put("fundCode", fundCode);
        input.put("fundName", fundName);
        input.put("analysisDate", LocalDate.now().toString());
        input.put("batchNo", batchNo);
        input.put("debateMaxRounds", debateMaxRounds);
        input.put("providerType", providerType);
        input.put("baseUrl", baseUrl);
        input.put("apiKey", apiKey);
        input.put("modelId", modelId);
        return input;
    }

    /**
     * 在根 observation 作用域内执行整条管线，使 7 个节点 span 与节点内 LLM GenAI span 作为其子 span
     * 嵌套。根 observation 携带 fundCode/fundName/batchNo/providerType/modelId 业务属性供筛选与关联。
     * 观测层不阻断业务：Micrometer/OTel 的 handler 异常被内部吞掉，业务异常照常抛出。
     */
    private OverAllState executeGraphTraced(Map<String, Object> input, String fundCode, String fundName,
                                            String batchNo, String providerType, String modelId) {
        return Observation.createNotStarted(ROOT_OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("fundCode", fundCode)
                .lowCardinalityKeyValue("providerType", providerType)
                .lowCardinalityKeyValue("modelId", modelId)
                .highCardinalityKeyValue("fundName", fundName)
                .highCardinalityKeyValue("batchNo", batchNo)
                .observe(() -> executeGraph(input));
    }

    private OverAllState executeGraph(Map<String, Object> input) {
        CompiledGraph graph = graphBuilder.build();
        return graph.invoke(input)
                .orElseThrow(() -> new RuntimeException("Graph execution returned empty state"));
    }

    @SuppressWarnings("unchecked")
    private AnalysisResult extractResult(OverAllState state, String batchNo) {
        return new AnalysisResult(batchNo,
                (Map<String, AgentReport>) state.value("agentReports").orElse(Map.of()),
                (DebateRecord) state.value("debateRecord").orElse(null),
                (String) state.value("traderAdvice").orElse(""),
                (String) state.value("riskAssessment").orElse(""),
                (String) state.value("portfolioAdvice").orElse(""),
                (String) state.value("finalReport").orElse(""));
    }

    public record AnalysisResult(
            String batchNo, Map<String, AgentReport> agentReports, DebateRecord debate,
            String traderAdvice, String riskAssessment, String portfolioAdvice, String finalReport
    ) {
    }
}
