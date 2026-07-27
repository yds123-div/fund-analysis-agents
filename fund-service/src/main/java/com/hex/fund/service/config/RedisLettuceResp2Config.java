package com.hex.fund.service.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 强制 Lettuce 使用 RESP2 协议。
 * Lettuce 默认会发 HELLO 命令协商 RESP3，但 Redis 7.x 启用 requirepass 时，
 * HELLO 必须先认证，握手时序不匹配会报 NOAUTH HELLO 导致连接失败。
 * 强制 RESP2 后不发 HELLO，直接 AUTH + 命令，规避此问题。
 */
@Configuration
public class RedisLettuceResp2Config {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceResp2Customizer() {
        return builder -> builder.clientOptions(
                ClientOptions.builder().protocolVersion(ProtocolVersion.RESP2).build());
    }
}
