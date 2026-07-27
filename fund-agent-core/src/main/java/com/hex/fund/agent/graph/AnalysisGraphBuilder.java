package com.hex.fund.agent.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.hex.fund.agent.graph.node.*;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.progress.TaskProgressHolder;
import com.hex.fund.datasource.core.DataSourceManager;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 分析流程图构建器 — 构建 StateGraph 并编译。
 * 流程: START → data_collection → parallel_analysis → debate → trader → risk_manager → portfolio_advisor → report_generator → END
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisGraphBuilder {

    private final DataSourceManager dataSourceManager;
    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;
    private final ObservationRegistry observationRegistry;

    /** 构建并编译分析流程图 */
    public CompiledGraph build() {
        try {
            OverAllState state = new OverAllState().registerKeyAndStrategy(buildStateKeys());
            StateGraph graph = buildGraph(state);
            CompiledGraph compiled = graph.compile();
            log.info("分析流程图编译成功");
            return compiled;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build analysis graph", e);
        }
    }

    private Map<String, KeyStrategy> buildStateKeys() {
        KeyStrategy replace = new ReplaceStrategy();
        return Stream.of(
                "fundCode", "fundName", "analysisDate", "batchNo",
                "providerType", "baseUrl", "apiKey", "modelId",
                "debateMaxRounds", "metadata", "agentReports",
                "debateRecord", "traderAdvice", "riskAssessment",
                "portfolioAdvice", "finalReport"
        ).collect(Collectors.toMap(k -> k, k -> replace));
    }

    private StateGraph buildGraph(OverAllState state) throws Exception {
        return new StateGraph(state)
                .addNode("data_collection", AsyncNodeAction.node_async(
                        traced("data_collection", new DataCollectionNode(dataSourceManager, progressHolder))))
                .addNode("parallel_analysis", AsyncNodeAction.node_async(
                        traced("parallel_analysis", new AnalystParallelNode(llmService, promptLoader, progressHolder))))
                .addNode("debate", AsyncNodeAction.node_async(
                        traced("debate", new DebateNode(llmService, promptLoader, progressHolder))))
                .addNode("trader", AsyncNodeAction.node_async(
                        traced("trader", new TraderNode(llmService, promptLoader, progressHolder))))
                .addNode("risk_manager", AsyncNodeAction.node_async(
                        traced("risk_manager", new RiskManagerNode(llmService, promptLoader, progressHolder))))
                .addNode("portfolio_advisor", AsyncNodeAction.node_async(
                        traced("portfolio_advisor", new PortfolioAdvisorNode(llmService, promptLoader, progressHolder))))
                .addNode("report_generator", AsyncNodeAction.node_async(
                        traced("report_generator", new ReportGeneratorNode(llmService, promptLoader, progressHolder))))
                .addEdge(StateGraph.START, "data_collection")
                .addEdge("data_collection", "parallel_analysis")
                .addEdge("parallel_analysis", "debate")
                .addEdge("debate", "trader")
                .addEdge("trader", "risk_manager")
                .addEdge("risk_manager", "portfolio_advisor")
                .addEdge("portfolio_advisor", "report_generator")
                .addEdge("report_generator", StateGraph.END);
    }

    /** 用可追踪节点动作装饰器包裹节点，使节点执行产生一条名为 nodeName 的 observation（span）。 */
    private NodeAction traced(String nodeName, NodeAction action) {
        return new TracedNodeAction(nodeName, action, observationRegistry);
    }
}
