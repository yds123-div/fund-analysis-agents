package com.hex.fund.service.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存配置，按数据类型设置不同 TTL。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        var jsonSerializer = RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer());
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(jsonSerializer).entryTtl(Duration.ofHours(1));
        return RedisCacheManager.builder(factory).cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "fundBasic", defaultConfig.entryTtl(Duration.ofHours(24)),
                        "fundEstimate", defaultConfig.entryTtl(Duration.ofSeconds(30)),
                        "navHistory", defaultConfig.entryTtl(Duration.ofHours(12)),
                        "fundHoldings", defaultConfig.entryTtl(Duration.ofDays(7)),
                        "newsData", defaultConfig.entryTtl(Duration.ofHours(1))
                )).build();
    }
}
