package com.hex.fund.common.progress;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存级分析进度容器，各 Graph Node 上报进度，前端轮询读取。
 * 由 Spring 配置类注册为 Bean。
 */
public class TaskProgressHolder {

    private final ConcurrentHashMap<String, TaskProgress> store = new ConcurrentHashMap<>();

    public void update(String batchNo, int progress, String stage) {
        store.put(batchNo, new TaskProgress(progress, stage, System.currentTimeMillis()));
    }

    public Optional<TaskProgress> get(String batchNo) {
        return Optional.ofNullable(store.get(batchNo));
    }

    /**
     * 清理超过30分钟的已完成条目，由外部定时调用。
     */
    public void cleanup() {
        long threshold = System.currentTimeMillis() - 30 * 60 * 1000;
        store.entrySet().removeIf(e ->
                e.getValue().progress() >= 100 && e.getValue().updatedAt() < threshold);
    }

    public record TaskProgress(int progress, String stage, long updatedAt) {
    }
}
