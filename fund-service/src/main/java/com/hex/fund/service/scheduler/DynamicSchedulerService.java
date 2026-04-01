package com.hex.fund.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.service.analysis.AnalysisService;
import com.hex.fund.service.entity.AnalysisTask;
import com.hex.fund.service.mapper.AnalysisTaskMapper;
import com.hex.fund.service.notification.NotificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态定时任务调度服务，从数据库加载任务配置并动态注册/注销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSchedulerService {

    /** 系统缓存刷新任务定义 */
    private static final List<String[]> SYSTEM_TASKS = List.of(
            new String[]{"开盘前数据预热", "0 15 9 * * MON-FRI"},
            new String[]{"午间数据刷新", "0 5 12 * * MON-FRI"},
            new String[]{"收盘后数据更新", "0 10 15 * * MON-FRI"},
            new String[]{"每日新闻刷新", "0 0 8,12,18 * * *"},
            new String[]{"Redis心跳检测", "0 */5 * * * *"},
            new String[]{"MySQL心跳检测", "0 */5 * * * *"});
    private final TaskScheduler taskScheduler;
    private final AnalysisTaskMapper taskMapper;
    private final AnalysisService analysisService;
    private final NotificationService notificationService;
    private final SchedulerService schedulerService;
    private final CacheRefreshService cacheRefreshService;
    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private Map<String, Runnable> systemTaskHandlers;

    @PostConstruct
    public void init() {
        systemTaskHandlers = Map.of(
                "开盘前", cacheRefreshService::refreshPreMarket,
                "午间", cacheRefreshService::refreshMidDay,
                "收盘后", cacheRefreshService::refreshPostMarket,
                "新闻", cacheRefreshService::refreshNews,
                "Redis心跳", schedulerService::redisHeartbeat,
                "MySQL心跳", schedulerService::mysqlHeartbeat);
        initSystemTasks();
        loadAndRegisterAll();
        log.info("动态调度器初始化完成，已注册 {} 个任务", runningTasks.size());
    }

    /** 从数据库加载所有启用的任务并注册到调度器 */
    public void loadAndRegisterAll() {
        List<AnalysisTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<AnalysisTask>().eq(AnalysisTask::getEnabled, 1));
        tasks.forEach(this::registerTask);
    }

    /** 注册单个定时任务 */
    public void registerTask(AnalysisTask task) {
        unregisterTask(task.getId());
        if (task.getEnabled() == null || !task.getEnabled()) {
            return;
        }
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
            return;
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeTask(task), new CronTrigger(task.getCronExpression()));
            runningTasks.put(task.getId(), future);
            log.info("定时任务已注册: id={}, 基金={}, cron={}",
                    task.getId(), task.getFundCode(), task.getCronExpression());
        } catch (Exception e) {
            log.error("定时任务注册失败: id={}, cron={}, 原因={}",
                    task.getId(), task.getCronExpression(), e.getMessage());
        }
    }

    /** 注销定时任务 */
    public void unregisterTask(Long taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("定时任务已注销: id={}", taskId);
        }
    }

    /** 手动触发一次任务执行 */
    public void triggerOnce(Long taskId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task != null) {
            taskScheduler.schedule(() -> executeTask(task), java.time.Instant.now());
        }
    }

    /** 保存任务配置并同步调度器 */
    public void saveAndSync(AnalysisTask task) {
        if (task.getId() != null) {
            taskMapper.updateById(task);
            unregisterTask(task.getId());
        } else {
            taskMapper.insert(task);
        }
        if (Boolean.TRUE.equals(task.getEnabled())) {
            registerTask(task);
        }
    }

    /** 删除任务并注销调度 */
    public void deleteAndSync(Long taskId) {
        unregisterTask(taskId);
        taskMapper.deleteById(taskId);
    }

    public int getRunningCount() {
        return runningTasks.size();
    }

    // ---- 组合方法 ----

    private void executeTask(AnalysisTask task) {
        if ("SYSTEM".equals(task.getTaskType())) {
            executeSystemTask(task);
            return;
        }
        if (!schedulerService.isTradingDay() && !"FORCE".equals(task.getTaskType())) {
            log.debug("非交易日，跳过任务: id={}", task.getId());
            return;
        }
        try {
            log.info("定时任务执行: id={}, 基金={}, 类型={}, 超时={}分钟",
                    task.getId(), task.getFundCode(),
                    task.getTaskType(), task.getTimeoutMinutes());
            analysisService.triggerAnalysis(task.getFundCode(), task.getFundCode(),
                    "SCHEDULED", task.getTimeoutMinutes());
        } catch (Exception e) {
            log.error("定时任务执行失败: id={}, 基金={}",
                    task.getId(), task.getFundCode(), e);
        }
    }

    private void executeSystemTask(AnalysisTask task) {
        try {
            String desc = task.getDescription();
            log.info("系统任务执行: id={}, {}", task.getId(), desc);
            systemTaskHandlers.entrySet().stream()
                    .filter(e -> desc.contains(e.getKey()))
                    .findFirst()
                    .ifPresentOrElse(
                            e -> e.getValue().run(),
                            () -> log.warn("未知系统任务: {}", desc));
        } catch (Exception e) {
            log.error("系统任务执行失败: id={}, desc={}",
                    task.getId(), task.getDescription(), e);
        }
    }

    /** 初始化系统任务（自动补充缺失的任务） */
    private void initSystemTasks() {
        List<AnalysisTask> existing = taskMapper.selectList(
                new LambdaQueryWrapper<AnalysisTask>().eq(AnalysisTask::getTaskType, "SYSTEM"));
        for (String[] def : SYSTEM_TASKS) {
            boolean exists = existing.stream()
                    .anyMatch(t -> def[0].equals(t.getDescription()));
            if (!exists) {
                AnalysisTask task = AnalysisTask.builder()
                        .userId(1L).fundCode("SYSTEM").taskType("SYSTEM")
                        .cronExpression(def[1]).enabled(true).description(def[0]).build();
                taskMapper.insert(task);
                log.info("系统任务已创建: {}, cron={}", def[0], def[1]);
            }
        }
    }
}
