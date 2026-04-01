package com.hex.fund.datasource.core;

import cn.hutool.json.JSONUtil;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.datasource.model.FundManagerData;
import com.hex.fund.datasource.model.HoldingData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 数据源统一管理器，支持优先级注册、多源容灾降级、Redis 缓存兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceManager {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_PREFIX = "ds:fallback:";
    private final Map<String, AdapterEntry> adapters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    /**
     * 带优先级注册数据源适配器，priority 值越小优先级越高。
     */
    public void register(DataSourceAdapter adapter, int priority) {
        adapters.put(adapter.getSourceCode(), new AdapterEntry(adapter, priority));
        log.info("数据源已注册: {} ({}) 优先级={}", adapter.getSourceName(), adapter.getSourceCode(), priority);
    }

    public Optional<DataSourceAdapter> getAdapter(String sourceCode) {
        AdapterEntry entry = adapters.get(sourceCode);
        return entry == null ? Optional.empty() : Optional.of(entry.adapter);
    }

    /**
     * 按优先级排序后返回第一个可用的适配器。
     */
    public Optional<DataSourceAdapter> getFirstAvailable() {
        return sortedAdapters().stream().filter(DataSourceAdapter::isAvailable).findFirst();
    }

    public List<DataSourceAdapter> getAllAdapters() {
        return sortedAdapters();
    }

    public Map<String, Boolean> healthCheck() {
        Map<String, Boolean> result = new ConcurrentHashMap<>();
        adapters.forEach((code, entry) -> {
            try {
                result.put(code, entry.adapter.isAvailable());
            } catch (Exception e) {
                result.put(code, false);
            }
        });
        return result;
    }

    /**
     * 核心容灾方法：按优先级依次尝试每个可用适配器执行 action，
     * 全部失败后尝试从 Redis 缓存读取兜底数据。
     * 成功获取数据后写入 Redis 缓存。
     */
    public <T> T getWithFallback(Function<DataSourceAdapter, T> action,
                                 String cacheKey, Class<T> type) {
        for (DataSourceAdapter adapter : sortedAdapters()) {
            if (!adapter.isAvailable()) continue;
            try {
                T result = action.apply(adapter);
                if (result != null) {
                    cacheToRedis(cacheKey, result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 执行失败, 尝试下一个: {}", adapter.getSourceCode(), e.getMessage());
            }
        }
        log.warn("所有数据源均不可用, 尝试 Redis 缓存兜底: {}", cacheKey);
        return readFromRedis(cacheKey, type);
    }

    /**
     * 聚合多数据源获取基金基本信息（带容灾）。
     */
    public FundBasicData getAggregatedFundBasic(String fundCode) {
        return getWithFallback(a -> a.getFundBasic(fundCode),
                CACHE_PREFIX + "basic:" + fundCode, FundBasicData.class);
    }

    /**
     * 聚合多数据源获取基金持仓（带容灾）。
     */
    @SuppressWarnings("unchecked")
    public List<HoldingData> getAggregatedHoldings(String fundCode) {
        List<HoldingData> result = getWithFallback(a -> a.getFundHoldings(fundCode, null),
                CACHE_PREFIX + "holdings:" + fundCode, List.class);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 聚合多数据源获取基金经理（带容灾）。
     */
    public FundManagerData getAggregatedManager(String fundCode) {
        return getWithFallback(a -> a.getFundManager(fundCode),
                CACHE_PREFIX + "manager:" + fundCode, FundManagerData.class);
    }

    // ------ 内部方法 ------

    private List<DataSourceAdapter> sortedAdapters() {
        return adapters.values().stream()
                .sorted(Comparator.comparingInt(e -> e.priority))
                .map(e -> e.adapter).toList();
    }

    private void cacheToRedis(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入 Redis 缓存失败: {}", e.getMessage());
        }
    }

    private <T> T readFromRedis(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            return JSONUtil.toBean(json, type);
        } catch (Exception e) {
            log.debug("读取 Redis 缓存失败: {}", e.getMessage());
            return null;
        }
    }

    private record AdapterEntry(DataSourceAdapter adapter, int priority) {
    }
}
