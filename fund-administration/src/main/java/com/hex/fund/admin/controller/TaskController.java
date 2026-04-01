package com.hex.fund.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.progress.TaskProgressHolder;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.service.entity.TaskExecution;
import com.hex.fund.service.mapper.TaskExecutionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务中心 API：进度查询与执行记录管理。
 */
@Slf4j
@Tag(name = "Task", description = "任务中心")
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskExecutionMapper executionMapper;
    private final TaskProgressHolder progressHolder;
    private final DataSourceManager dataSourceManager;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Operation(summary = "查询任务进度")
    @GetMapping("/progress/{batchNo}")
    public ApiResponse<Map<String, Object>> getProgress(@PathVariable String batchNo) {
        return progressHolder.get(batchNo)
                .map(p -> ApiResponse.ok(Map.<String, Object>of("progress", p.progress(), "stage", p.stage())))
                .orElseGet(() -> {
                    var exec = executionMapper.selectOne(
                            new LambdaQueryWrapper<TaskExecution>().eq(TaskExecution::getBatchNo, batchNo));
                    if (exec == null) return ApiResponse.fail(404, "任务不存在");
                    return ApiResponse.ok(Map.of("progress", exec.getProgress() != null ? exec.getProgress() : 0,
                            "stage", exec.getCurrentStage() != null ? exec.getCurrentStage() : ""));
                });
    }

    @Operation(summary = "SSE 进度推送流")
    @GetMapping(value = "/progress-stream/{batchNo}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String batchNo) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.put(batchNo, emitter);

        new Thread(() -> {
            try {
                int lastProgress = -1;
                while (true) {
                    var progress = progressHolder.get(batchNo);
                    if (progress.isPresent()) {
                        var p = progress.get();
                        if (p.progress() != lastProgress) {
                            emitter.send(SseEmitter.event()
                                    .id(batchNo)
                                    .name("progress")
                                    .data(Map.of("progress", p.progress(), "stage", p.stage()))
                                    .build());
                            lastProgress = p.progress();
                            if (p.progress() >= 100 || p.progress() < 0) break;
                        }
                    }
                    Thread.sleep(500);
                }
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                log.warn("SSE 连接中断: {}", batchNo);
                try {
                    emitter.completeWithError(e);
                } catch (Throwable ex) {
                    log.debug("emitter error", ex);
                }
            } finally {
                emitters.remove(batchNo);
            }
        }).start();

        return emitter;
    }

    @Operation(summary = "任务执行列表")
    @GetMapping("/executions")
    public ApiResponse<List<TaskExecution>> listExecutions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fundCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        var wrapper = new LambdaQueryWrapper<TaskExecution>()
                .eq(status != null && !status.isBlank(), TaskExecution::getStatus, status)
                .eq(fundCode != null && !fundCode.isBlank(), TaskExecution::getFundCode, fundCode)
                .orderByDesc(TaskExecution::getCreatedAt)
                .last("LIMIT " + Math.min(size, 100) + " OFFSET " + Math.max(0, (page - 1) * size));
        List<TaskExecution> list = executionMapper.selectList(wrapper);
        list.forEach(e -> {
            if (e.getFundName() == null || e.getFundName().isBlank()) {
                try {
                    FundBasicData basic = dataSourceManager.getAggregatedFundBasic(e.getFundCode());
                    if (basic != null && basic.getFundName() != null) e.setFundName(basic.getFundName());
                } catch (Exception ignored) {
                }
            }
        });
        return ApiResponse.ok(list);
    }
}
