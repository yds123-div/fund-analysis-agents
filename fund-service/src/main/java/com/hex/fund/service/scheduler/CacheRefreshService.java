package com.hex.fund.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 系统缓存刷新服务，由系统定时任务调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheRefreshService {

    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;

    /**
     * 开盘前预热：刷新基金基本信息和净值历史缓存。
     */
    public void refreshPreMarket() {
        log.info("[系统任务] 开盘前数据预热");
        evictSpringCache("fundBasic");
        evictSpringCache("navHistory");
        evictSpringCache("fundHoldings");
        deleteRedisKeys("ds:fallback:*");
    }

    /**
     * 午间刷新：市场指数、新闻、估值。
     */
    public void refreshMidDay() {
        log.info("[系统任务] 午间数据刷新");
        redisTemplate.delete("dashboard:market:indices");
        evictSpringCache("fundEstimate");
        deleteRedisKeys("news:*");
    }

    /**
     * 收盘后更新：净值、数据源兜底。
     */
    public void refreshPostMarket() {
        log.info("[系统任务] 收盘后数据更新");
        evictSpringCache("navHistory");
        evictSpringCache("fundEstimate");
        evictSpringCache("fundBasic");
        deleteRedisKeys("ds:fallback:*");
        redisTemplate.delete("dashboard:market:indices");
    }

    /**
     * 新闻缓存刷新。
     */
    public void refreshNews() {
        log.info("[系统任务] 新闻缓存刷新");
        deleteRedisKeys("news:*");
    }

    private void evictSpringCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }

    private void deleteRedisKeys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("删除Redis缓存失败: pattern={}, error={}", pattern, e.getMessage());
        }
    }
}
