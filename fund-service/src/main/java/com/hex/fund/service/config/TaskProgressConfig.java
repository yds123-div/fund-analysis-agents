package com.hex.fund.service.config;

import com.hex.fund.common.progress.TaskProgressHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 任务进度容器和调度器配置。
 */
@Configuration
public class TaskProgressConfig {

    @Bean
    public TaskProgressHolder taskProgressHolder() {
        return new TaskProgressHolder();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("fund-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Scheduled(fixedRate = 600_000)
    public void cleanupProgress() {
        taskProgressHolder().cleanup();
    }
}
