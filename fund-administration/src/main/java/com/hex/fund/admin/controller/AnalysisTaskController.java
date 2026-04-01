package com.hex.fund.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.service.entity.AnalysisTask;
import com.hex.fund.service.mapper.AnalysisTaskMapper;
import com.hex.fund.service.scheduler.DynamicSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务管理 API，支持动态配置和手动触发。
 */
@Tag(name = "AnalysisTask", description = "定时分析任务管理")
@RestController
@RequestMapping("/api/tasks/config")
@RequiredArgsConstructor
public class AnalysisTaskController {

    private final AnalysisTaskMapper taskMapper;
    private final DynamicSchedulerService dynamicScheduler;

    @Operation(summary = "查询任务配置列表")
    @GetMapping
    public ApiResponse<List<AnalysisTask>> list() {
        return ApiResponse.ok(taskMapper.selectList(
                new LambdaQueryWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getUserId, SecurityContext.getCurrentUserId())
                        .or().eq(AnalysisTask::getTaskType, "SYSTEM")));
    }

    @Operation(summary = "新增或更新任务配置")
    @PostMapping
    public ApiResponse<Void> save(@RequestBody AnalysisTask task) {
        task.setUserId(SecurityContext.getCurrentUserId());
        dynamicScheduler.saveAndSync(task);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除任务配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dynamicScheduler.deleteAndSync(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "手动触发一次任务")
    @PostMapping("/{id}/trigger")
    public ApiResponse<Void> trigger(@PathVariable Long id) {
        dynamicScheduler.triggerOnce(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取调度器状态")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of("runningTasks", dynamicScheduler.getRunningCount()));
    }
}
