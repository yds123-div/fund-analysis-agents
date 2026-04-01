package com.hex.fund.service.analysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.agent.orchestrator.AnalysisOrchestrator;
import com.hex.fund.common.enums.AnalysisPhase;
import com.hex.fund.common.enums.ReportType;
import com.hex.fund.common.enums.TaskStatus;
import com.hex.fund.common.progress.TaskProgressHolder;
import com.hex.fund.service.ai.AiModelService;
import com.hex.fund.service.entity.AnalysisReport;
import com.hex.fund.service.entity.TaskExecution;
import com.hex.fund.service.mapper.AnalysisReportMapper;
import com.hex.fund.service.mapper.SystemConfigMapper;
import com.hex.fund.service.mapper.TaskExecutionMapper;
import com.hex.fund.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基金分析编排服务，支持异步执行与进度追踪。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisOrchestrator orchestrator;
    private final AiModelService aiModelService;
    private final AnalysisReportMapper reportMapper;
    private final TaskExecutionMapper executionMapper;
    private final SystemConfigMapper configMapper;
    private final NotificationService notificationService;
    private final TaskProgressHolder progressHolder;

    /** 触发分析：创建执行记录，异步执行，立即返回 batchNo */
    public String triggerAnalysis(String fundCode, String fundName) {
        return triggerAnalysis(fundCode, fundName, "MANUAL");
    }

    public String triggerAnalysis(String fundCode, String fundName, String triggerType) {
        return triggerAnalysis(fundCode, fundName, triggerType, null);
    }

    /** 触发分析（指定触发类型和超时时间） */
    public String triggerAnalysis(String fundCode, String fundName, String triggerType, Integer timeoutMinutes) {
        configureOrchestrator();
        String batchNo = generateBatchNo();
        TaskExecution execution = createExecution(fundCode, fundName, batchNo, triggerType);
        executionMapper.insert(execution);
        progressHolder.update(batchNo, 0, "等待执行");
        executeAsync(execution.getId(), fundCode, fundName, batchNo, timeoutMinutes);
        return batchNo;
    }

    /** 异步执行分析流程（支持超时控制） */
    @Async
    public void executeAsync(Long executionId, String fundCode, String fundName, String batchNo, Integer timeoutMinutes) {
        updateExecution(executionId, TaskStatus.RUNNING, 0, "开始分析", null);
        int timeout = timeoutMinutes != null && timeoutMinutes > 0 ? timeoutMinutes : 30;
        try {
            var result = invokeOrchestrator(fundCode, fundName, timeout);
            handleSuccess(executionId, batchNo, fundCode, result);
        } catch (TimeoutException e) {
            handleTimeout(executionId, batchNo, fundCode, timeout);
        } catch (Exception e) {
            handleFailure(executionId, batchNo, fundCode, e);
        }
    }

    public AnalysisReport getReport(String batchNo) {
        return reportMapper.selectOne(
                new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getBatchNo, batchNo));
    }

    public List<AnalysisReport> listReports(String fundCode, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(0, (page - 1) * safeSize);
        var wrapper = new LambdaQueryWrapper<AnalysisReport>().orderByDesc(AnalysisReport::getCreatedAt);
        if (fundCode != null && !fundCode.isBlank()) wrapper.eq(AnalysisReport::getFundCode, fundCode);
        wrapper.last("LIMIT " + safeSize + " OFFSET " + safeOffset);
        return reportMapper.selectList(wrapper);
    }

    // ---- 组合方法 ----

    private AnalysisOrchestrator.AnalysisResult invokeOrchestrator(String fundCode, String fundName, int timeout)
            throws Exception {
        CompletableFuture<AnalysisOrchestrator.AnalysisResult> future = CompletableFuture.supplyAsync(() -> {
            var provider = aiModelService.resolveAgentProvider("default", "deep_think");
            return orchestrator.analyze(fundCode, fundName, ReportType.DAILY,
                    provider.type(), provider.baseUrl(), provider.apiKey(), provider.modelId());
        });
        return future.get(timeout, TimeUnit.MINUTES);
    }

    private void handleSuccess(Long executionId, String batchNo, String fundCode,
                               AnalysisOrchestrator.AnalysisResult result) {
        saveReport(result, fundCode);
        progressHolder.update(batchNo, AnalysisPhase.COMPLETED.getProgress(), AnalysisPhase.COMPLETED.getDesc());
        updateExecution(executionId, TaskStatus.SUCCESS, 100, "分析完成", null);
        notifyCompletion(fundCode, result);
    }

    private void handleTimeout(Long executionId, String batchNo, String fundCode, int timeout) {
        log.error("分析执行超时: 基金={}, 批次={}, 超时={}分钟", fundCode, batchNo, timeout);
        progressHolder.update(batchNo, -1, "执行超时");
        updateExecution(executionId, TaskStatus.TIMEOUT, -1, "执行超时", "任务执行超过" + timeout + "分钟超时");
    }

    private void handleFailure(Long executionId, String batchNo, String fundCode, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error("分析执行失败: 基金={}, 批次={}", fundCode, batchNo, cause);
        progressHolder.update(batchNo, -1, "执行失败: " + cause.getMessage());
        updateExecution(executionId, TaskStatus.FAILED, -1, "执行失败", cause.getMessage());
    }

    private void notifyCompletion(String fundCode, AnalysisOrchestrator.AnalysisResult result) {
        try {
            notificationService.notifyAnalysisComplete(1L, null, fundCode,
                    result.finalReport() != null ? result.finalReport() : "分析完成");
        } catch (Exception ex) {
            log.warn("分析完成通知发送失败: 基金={}, 原因={}", fundCode, ex.getMessage());
        }
    }

    private void configureOrchestrator() {
        orchestrator.configure(getDebateMaxRounds());
    }

    private String generateBatchNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private TaskExecution createExecution(String fundCode, String fundName, String batchNo, String triggerType) {
        return TaskExecution.builder()
                .fundCode(fundCode).fundName(fundName).batchNo(batchNo)
                .status(TaskStatus.PENDING.getCode()).progress(0)
                .currentStage("等待执行").triggerType(triggerType)
                .startTime(LocalDateTime.now()).retryCount(0).maxRetry(3).build();
    }

    private void updateExecution(Long id, TaskStatus status, int progress, String stage, String error) {
        TaskExecution update = new TaskExecution();
        update.setId(id);
        update.setStatus(status.getCode());
        update.setProgress(progress);
        update.setCurrentStage(stage);
        if (status == TaskStatus.SUCCESS || status == TaskStatus.FAILED) update.setEndTime(LocalDateTime.now());
        if (error != null) update.setErrorMessage(error);
        executionMapper.updateById(update);
    }

    private void saveReport(AnalysisOrchestrator.AnalysisResult result, String fundCode) {
        var reports = result.agentReports();
        var debate = result.debate();
        AnalysisReport report = AnalysisReport.builder()
                .userId(1L).batchNo(result.batchNo()).fundCode(fundCode)
                .reportType(ReportType.DAILY.name()).reportDate(LocalDate.now()).reportVersion(1)
                .fundAnalystResult(extractReport(reports, "fund_analyst"))
                .technicalAnalystResult(extractReport(reports, "technical_analyst"))
                .industryAnalystResult(extractReport(reports, "industry_analyst"))
                .sentimentAnalystResult(extractReport(reports, "sentiment_analyst"))
                .newsAnalystResult(extractReport(reports, "news_analyst"))
                .managerAnalystResult(extractReport(reports, "manager_analyst"))
                .bullishResearcherResult(extractDebateArguments(debate, true))
                .bearishResearcherResult(extractDebateArguments(debate, false))
                .debateSummary(debate != null ? debate.finalVerdict() : "")
                .traderResult(result.traderAdvice()).riskManagerResult(result.riskAssessment())
                .portfolioAdvisorResult(result.portfolioAdvice())
                .summary(result.finalReport()).createdAt(LocalDateTime.now()).build();
        reportMapper.insert(report);
        log.info("分析报告已保存: 批次={}, 基金={}", result.batchNo(), fundCode);
    }

    private String extractReport(Map<String, com.hex.fund.agent.model.AgentReport> reports, String agentId) {
        if (reports == null || !reports.containsKey(agentId)) return null;
        var r = reports.get(agentId);
        return r.summary() + "\n\n" + r.detailedAnalysis();
    }

    /** 从辩论记录中提取看多/看空研究员的各轮论点 */
    private String extractDebateArguments(com.hex.fund.agent.model.DebateRecord debate, boolean bullish) {
        if (debate == null || debate.rounds() == null || debate.rounds().isEmpty()) return null;
        var sb = new StringBuilder();
        for (var round : debate.rounds()) {
            sb.append("### 第").append(round.roundNumber()).append("轮\n\n");
            sb.append(bullish ? round.bullishArgument() : round.bearishArgument()).append("\n\n");
        }
        if (bullish && debate.consensus() != null) {
            sb.append("### 共识\n\n").append(debate.consensus());
        }
        if (!bullish && debate.divergence() != null) {
            sb.append("### 分歧\n\n").append(debate.divergence());
        }
        return sb.toString();
    }

    private int getDebateMaxRounds() {
        try {
            var config = configMapper.selectOne(
                    new LambdaQueryWrapper<com.hex.fund.service.entity.SystemConfig>()
                            .eq(com.hex.fund.service.entity.SystemConfig::getConfigKey, "debate_max_rounds"));
            return config != null ? Integer.parseInt(config.getConfigValue()) : 3;
        } catch (Exception e) {
            return 3;
        }
    }
}
