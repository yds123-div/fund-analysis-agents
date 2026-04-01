package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.agent.decision.PortfolioAdvisorAgent;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.model.AnalysisContext;
import com.hex.fund.agent.model.DebateRecord;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.enums.ReportType;
import com.hex.fund.common.progress.TaskProgressHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

/**
 * 组合顾问节点 — 从组合层面给出再平衡建议。
 */
@Slf4j
@RequiredArgsConstructor
public class PortfolioAdvisorNode implements NodeAction {

    private final LlmService llmService;
    private final PromptLoader promptLoader;
    private final TaskProgressHolder progressHolder;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, 92, "组合建议");
        DebateRecord debate = (DebateRecord) state.value("debateRecord").orElse(null);
        String traderAdvice = (String) state.value("traderAdvice").orElse("");
        String riskAssessment = (String) state.value("riskAssessment").orElse("");
        AnalysisContext context = new AnalysisContext(
                (String) state.value("fundCode").orElse(""),
                (String) state.value("fundName").orElse(""),
                LocalDate.now(), ReportType.DAILY, "", Map.of(), new ArrayList<>(), Map.of());
        PortfolioAdvisorAgent advisor = new PortfolioAdvisorAgent();
        advisor.configure(llmService, promptLoader,
                (String) state.value("providerType").orElse(""),
                (String) state.value("baseUrl").orElse(""),
                (String) state.value("apiKey").orElse(""),
                (String) state.value("modelId").orElse(""));
        String advice = advisor.advise(context, debate, traderAdvice, riskAssessment);
        log.info("[组合建议] 组合顾问建议生成完成");
        return Map.of("portfolioAdvice", advice);
    }
}
