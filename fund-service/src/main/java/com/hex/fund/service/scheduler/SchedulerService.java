package com.hex.fund.service.scheduler;

import com.hex.fund.datasource.core.DataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Set;

/**
 * 基础调度服务，仅提供健康检查和交易日判断等工具方法。
 * 定时分析任务统一由 DynamicSchedulerService 管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final Set<MonthDay> HOLIDAYS_2026 = Set.of(
            MonthDay.of(1, 1), MonthDay.of(1, 2),
            MonthDay.of(1, 26), MonthDay.of(1, 27), MonthDay.of(1, 28),
            MonthDay.of(1, 29), MonthDay.of(1, 30), MonthDay.of(2, 1),
            MonthDay.of(4, 4), MonthDay.of(4, 5), MonthDay.of(4, 6),
            MonthDay.of(5, 1), MonthDay.of(5, 2), MonthDay.of(5, 3),
            MonthDay.of(5, 4), MonthDay.of(5, 5),
            MonthDay.of(5, 31), MonthDay.of(6, 1), MonthDay.of(6, 2),
            MonthDay.of(10, 1), MonthDay.of(10, 2), MonthDay.of(10, 3),
            MonthDay.of(10, 4), MonthDay.of(10, 5), MonthDay.of(10, 6), MonthDay.of(10, 7)
    );
    private final DataSourceManager dataSourceManager;
    private final StringRedisTemplate redisTemplate;
    private final DataSource dataSource;

    /**
     * 数据源健康检查 — 每5分钟。
     */
    @Scheduled(fixedRate = 300_000)
    public void healthCheck() {
        Map<String, Boolean> result = dataSourceManager.healthCheck();
        result.forEach((code, ok) -> {
            if (!ok) log.warn("数据源离线: {}", code);
        });
    }

    public boolean isTradingDay() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !HOLIDAYS_2026.contains(MonthDay.from(today));
    }

    /** Redis 心跳检测 */
    public void redisHeartbeat() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            log.info("Redis 心跳: {}", pong);
        } catch (Exception e) {
            log.error("Redis 心跳失败: {}", e.getMessage());
        }
    }

    /** MySQL 心跳检测 */
    public void mysqlHeartbeat() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            log.info("MySQL 心跳: {}", valid ? "OK" : "FAIL");
            if (!valid) log.error("MySQL 连接验证失败");
        } catch (Exception e) {
            log.error("MySQL 心跳失败: {}", e.getMessage());
        }
    }
}
