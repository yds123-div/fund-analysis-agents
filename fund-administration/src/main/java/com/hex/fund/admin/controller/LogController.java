package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 系统日志查看 API，支持多日志文件、历史查询和 SSE 实时推送。
 */
@Slf4j
@Tag(name = "Log", description = "系统日志")
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final String LOG_DIR = "logs";
    private static final Map<String, String> LOG_FILES = Map.of(
            "app", "app.log", "error", "error.log", "llm", "llm.log");

    private String resolveLogPath(String type) {
        return Paths.get(LOG_DIR, LOG_FILES.getOrDefault(type, "app.log")).toString();
    }

    @Operation(summary = "可用日志类型")
    @GetMapping("/types")
    public ApiResponse<List<Map<String, String>>> types() {
        return ApiResponse.ok(List.of(
                Map.of("key", "app", "label", "应用日志"),
                Map.of("key", "error", "label", "错误日志"),
                Map.of("key", "llm", "label", "模型日志")));
    }

    @Operation(summary = "获取最近日志")
    @GetMapping("/recent")
    public ApiResponse<List<String>> recent(
            @RequestParam(defaultValue = "200") int lines,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "app") String type) {
        try {
            String logFile = resolveLogPath(type);
            Set<String> levels = parseLevels(level);
            List<String> allLines = readTailLines(logFile, lines * 2);
            if (!levels.isEmpty()) {
                allLines = allLines.stream().filter(l -> levels.stream().anyMatch(l::contains)).toList();
            }
            int from = Math.max(0, allLines.size() - lines);
            return ApiResponse.ok(allLines.subList(from, allLines.size()));
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取日志失败: " + e.getMessage());
        }
    }

    @Operation(summary = "SSE 实时日志流")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String level,
                             @RequestParam(defaultValue = "app") String type) {
        SseEmitter emitter = new SseEmitter(300_000L);
        var stopped = new java.util.concurrent.atomic.AtomicBoolean(false);
        emitter.onCompletion(() -> stopped.set(true));
        emitter.onTimeout(() -> stopped.set(true));
        emitter.onError(e -> stopped.set(true));
        String logFile = resolveLogPath(type);
        Set<String> levels = parseLevels(level);
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> tailLogFile(emitter, logFile, levels, stopped));
        return emitter;
    }

    private Set<String> parseLevels(String level) {
        if (level == null || level.isBlank()) return Set.of();
        return Arrays.stream(level.split(",")).map(String::trim).map(String::toUpperCase)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private void tailLogFile(SseEmitter emitter, String logFile,
                             Set<String> levels, java.util.concurrent.atomic.AtomicBoolean stopped) {
        long deadline = System.currentTimeMillis() + 5 * 60 * 1000;
        Path path = Paths.get(logFile);
        if (!Files.exists(path)) {
            trySend(emitter, "日志文件不存在: " + logFile);
            tryComplete(emitter);
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            // 立即发送连接成功事件，触发前端 onopen
            emitter.send(SseEmitter.event().comment("connected"));
            // 定位到文件末尾，只推送新增日志
            raf.seek(Math.max(0, raf.length() - 4096));
            if (raf.getFilePointer() > 0) raf.readLine();
            while (raf.readLine() != null) { /* skip existing */ }
            long lastLength = raf.length();
            long lastHeartbeat = System.currentTimeMillis();
            while (!stopped.get() && System.currentTimeMillis() < deadline) {
                long currentLength = path.toFile().length();
                if (currentLength < lastLength) {
                    raf.seek(0);
                    lastLength = 0;
                }
                String line = raf.readLine();
                if (line != null) {
                    String decoded = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                    if (levels.isEmpty() || levels.stream().anyMatch(decoded::contains)) {
                        if (!trySendOrStop(emitter, SseEmitter.event().data(decoded), stopped)) break;
                    }
                    lastLength = raf.getFilePointer();
                    lastHeartbeat = System.currentTimeMillis();
                } else {
                    // 每15秒发送心跳保持连接活跃
                    if (System.currentTimeMillis() - lastHeartbeat > 15_000) {
                        if (!trySendOrStop(emitter, SseEmitter.event().comment("heartbeat"), stopped)) break;
                        lastHeartbeat = System.currentTimeMillis();
                    }
                    Thread.sleep(200);
                }
            }
            tryComplete(emitter);
        } catch (Exception e) {
            log.debug("日志流结束: {}", e.getMessage());
            tryComplete(emitter);
        }
    }

    private boolean trySendOrStop(SseEmitter emitter, SseEmitter.SseEventBuilder event,
                                  java.util.concurrent.atomic.AtomicBoolean stopped) {
        try {
            emitter.send(event);
            return true;
        } catch (Exception e) {
            stopped.set(true);
            return false;
        }
    }

    private void trySend(SseEmitter emitter, String data) {
        try { emitter.send(SseEmitter.event().data(data)); } catch (IOException ignored) { }
    }

    private void tryComplete(SseEmitter emitter) {
        try { emitter.complete(); } catch (Exception ignored) { }
    }

    private List<String> readTailLines(String logFile, int maxLines) throws IOException {
        Path path = Paths.get(logFile);
        if (!Files.exists(path)) return List.of("日志文件不存在: " + logFile);
        List<String> allLines = Files.readAllLines(path);
        int from = Math.max(0, allLines.size() - maxLines);
        return new ArrayList<>(allLines.subList(from, allLines.size()));
    }
}
