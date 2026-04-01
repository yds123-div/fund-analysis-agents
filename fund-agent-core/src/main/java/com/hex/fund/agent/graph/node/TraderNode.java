package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.agent.decision.TraderAgent;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AgentReport;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.enums.AnalysisPhase;
import com.hex.fund.common.enums.ReportType;
import com.hex.fund.common.progress.TaskProgressHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

/**
 * 交易建议节点 — 基于分析和辩论结果生成交易建议。
 */
@Slf4j
@RequiredArgsConstructor
public class TraderNode implements NodeAction {

    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, AnalysisPhase.TRADER.getProgress(), AnalysisPhase.TRADER.getDesc());
        Map<String, AgentReport> reports = (Map<String, AgentReport>) state.value("agentReports").orElse(Map.of());
        DebateRecord debate = (DebateRecord) state.value("debateRecord").orElse(null);
        AnalysisContext context = new AnalysisContext(
                (String) state.value("fundCode").orElse(""),
                (String) state.value("fundName").orElse(""),
                LocalDate.now(), ReportType.DAILY, "", reports, new ArrayList<>(), Map.of());
        TraderAgent trader = new TraderAgent();
        trader.configure(llmService, promptLoader,
                (String) state.value("providerType").orElse(""),
                (String) state.value("baseUrl").orElse(""),
                (String) state.value("apiKey").orElse(""),
                (String) state.value("modelId").orElse(""));
        String advice = trader.advise(context, debate);
        log.info("[交易建议] 交易建议生成完成");
        return Map.of("traderAdvice", advice);
    }
}
