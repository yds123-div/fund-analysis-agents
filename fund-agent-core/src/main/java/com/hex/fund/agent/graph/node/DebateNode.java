package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.agent.debate.DebateCoordinator;
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
 * 辩论节点 — 执行看多/看空研究员的多轮辩论。
 */
@Slf4j
@RequiredArgsConstructor
public class DebateNode implements NodeAction {

    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, AnalysisPhase.DEBATE.getProgress(), AnalysisPhase.DEBATE.getDesc());
        Map<String, AgentReport> reports = (Map<String, AgentReport>) state.value("agentReports").orElse(Map.of());
        int maxRounds = (int) state.value("debateMaxRounds").orElse(3);
        AnalysisContext context = new AnalysisContext(
                (String) state.value("fundCode").orElse(""),
                (String) state.value("fundName").orElse(""),
                LocalDate.now(), ReportType.DAILY, "", reports, new ArrayList<>(), Map.of());
        DebateCoordinator coordinator = new DebateCoordinator();
        coordinator.configure(llmService, promptLoader,
                (String) state.value("providerType").orElse(""),
                (String) state.value("baseUrl").orElse(""),
                (String) state.value("apiKey").orElse(""),
                (String) state.value("modelId").orElse(""));
        DebateRecord debate = coordinator.debate(context, maxRounds);
        log.info("[辩论] 辩论完成: 共识={}", debate.consensus());
        return Map.of("debateRecord", debate);
    }
}
