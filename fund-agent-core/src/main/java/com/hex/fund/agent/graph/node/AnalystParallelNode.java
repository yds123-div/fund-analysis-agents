package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.agent.analyst.*;
import com.hex.fund.agent.core.AbstractAnalysisAgent;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.enums.AnalysisPhase;
import com.hex.fund.common.enums.ReportType;
import com.hex.fund.common.progress.TaskProgressHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并行分析节点 — 使用虚拟线程并行执行 6 个分析师 Agent。
 */
@Slf4j
@RequiredArgsConstructor
public class AnalystParallelNode implements NodeAction {

    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, AnalysisPhase.PARALLEL_ANALYSIS.getProgress(),
                AnalysisPhase.PARALLEL_ANALYSIS.getDesc());
        AnalysisContext context = buildContext(state);
        String providerType = (String) state.value("providerType").orElse("");
        String baseUrl = (String) state.value("baseUrl").orElse("");
        String apiKey = (String) state.value("apiKey").orElse("");
        String modelId = (String) state.value("modelId").orElse("");
        Map<String, AgentReport> reports = executeAgentsInParallel(
                context, providerType, baseUrl, apiKey, modelId);
        log.info("[并行分析] {}/{} 个分析师Agent执行成功", reports.size(), 6);
        return Map.of("agentReports", reports);
    }

    @SuppressWarnings("unchecked")
    private AnalysisContext buildContext(OverAllState state) {
        return new AnalysisContext(
                (String) state.value("fundCode").orElse(""),
                (String) state.value("fundName").orElse(""),
                LocalDate.now(), ReportType.DAILY, "",
                new ConcurrentHashMap<>(), new ArrayList<>(),
                (Map<String, Object>) state.value("metadata").orElse(Map.of()));
    }

    private Map<String, AgentReport> executeAgentsInParallel(
            AnalysisContext context, String providerType,
            String baseUrl, String apiKey, String modelId) {
        List<AbstractAnalysisAgent> agents = List.of(
                new FundAnalystAgent(), new TechnicalAnalystAgent(), new IndustryAnalystAgent(),
                new ManagerAnalystAgent(), new SentimentAnalystAgent(), new NewsAnalystAgent());
        Map<String, AgentReport> reports = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<AgentReport>> futures = new HashMap<>();
            for (AbstractAnalysisAgent agent : agents) {
                futures.put(agent.getAgentId(), executor.submit(() -> {
                    agent.configure(llmService, promptLoader, providerType, baseUrl, apiKey, modelId);
                    return agent.analyze(context);
                }));
            }
            collectResults(futures, reports);
        }
        return reports;
    }

    private void collectResults(Map<String, Future<AgentReport>> futures,
                                Map<String, AgentReport> reports) {
        for (var entry : futures.entrySet()) {
            try {
                reports.put(entry.getKey(), entry.getValue().get(120, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("分析师Agent执行失败: {}，原因: {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
