package com.hex.fund.datasource.news;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 新闻聚合服务，支持降级：东方财富 → 新浪财经 → Redis 缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private final NewsApi newsApi;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取财经要闻，支持降级。
     */
    public List<Map<String, String>> getMarketNews(int count) {
        return fetchWithFallback("news:market", count,
                () -> newsApi.fetchEastMoneyNews(count),
                () -> newsApi.fetchSinaNews(count));
    }

    /**
     * 获取基金相关新闻，支持降级。
     */
    public List<Map<String, String>> getFundNews(String keyword, int count) {
        return fetchWithFallback("news:fund:" + keyword, count,
                () -> newsApi.fetchFundNews(keyword, count),
                () -> newsApi.fetchSinaNews(count)); // 降级到通用新闻
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchWithFallback(String cacheKey, int count,
                                                        NewsSupplier primary, NewsSupplier fallback) {
        // 尝试主数据源
        List<Map<String, String>> result = tryFetch(primary, "主数据源");
        if (!result.isEmpty()) {
            cacheResult(cacheKey, result);
            return result;
        }
        // 尝试备用数据源
        result = tryFetch(fallback, "备用数据源");
        if (!result.isEmpty()) {
            cacheResult(cacheKey, result);
            return result;
        }
        // 缓存兜底
        return readCache(cacheKey);
    }

    private List<Map<String, String>> tryFetch(NewsSupplier supplier, String sourceName) {
        try {
            List<Map<String, String>> result = supplier.get();
            if (result != null && !result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("{}获取新闻失败: {}", sourceName, e.getMessage());
        }
        return List.of();
    }

    private void cacheResult(String key, List<Map<String, String>> data) {
        try {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), CACHE_TTL);
        } catch (Exception e) {
            log.debug("缓存新闻失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> readCache(String key) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("使用缓存新闻: key={}", key);
                return (List<Map<String, String>>) (List<?>) JSONUtil.toList(cached, Map.class);
            }
        } catch (Exception e) {
            log.debug("读取缓存失败: {}", e.getMessage());
        }
        return List.of();
    }

    @FunctionalInterface
    interface NewsSupplier {

        List<Map<String, String>> get();
    }
}
