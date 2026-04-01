package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.agent.decision.RiskManagerAgent;
import com.hex.fund.agent.llm.LlmService;
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
 * 风控评估节点 — 评估交易建议的风险敞口。
 */
@Slf4j
@RequiredArgsConstructor
public class RiskManagerNode implements NodeAction {

    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, AnalysisPhase.RISK_MANAGER.getProgress(),
                AnalysisPhase.RISK_MANAGER.getDesc());
        DebateRecord debate = (DebateRecord) state.value("debateRecord").orElse(null);
        String traderAdvice = (String) state.value("traderAdvice").orElse("");
        AnalysisContext context = new AnalysisContext(
                (String) state.value("fundCode").orElse(""),
                (String) state.value("fundName").orElse(""),
                LocalDate.now(), ReportType.DAILY, "", Map.of(), new ArrayList<>(), Map.of());
        RiskManagerAgent riskMgr = new RiskManagerAgent();
        riskMgr.configure(llmService, promptLoader,
                (String) state.value("providerType").orElse(""),
                (String) state.value("baseUrl").orElse(""),
                (String) state.value("apiKey").orElse(""),
                (String) state.value("modelId").orElse(""));
        String assessment = riskMgr.assess(context, debate, traderAdvice);
        log.info("[风控评估] 风险评估完成");
        return Map.of("riskAssessment", assessment);
    }
}
