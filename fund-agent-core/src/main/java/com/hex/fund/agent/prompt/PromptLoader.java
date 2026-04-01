package com.hex.fund.agent.prompt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 提示词模板加载器 — 从 classpath 加载并缓存 prompt 模板。
 */
@Slf4j
@Component
public class PromptLoader {

    private static final String BASE_PATH = "prompts/";
    private final Cache<String, String> cache = Caffeine.newBuilder().maximumSize(50).build();

    /** 按路径加载提示词模板（如 "analyst/fund-analyst-system"） */
    public String load(String path) {
        return cache.get(path, this::readResource);
    }

    /** 加载并格式化提示词模板 */
    public String load(String path, Object... args) {
        String template = load(path);
        return args.length > 0 ? String.format(template, args) : template;
    }

    private String readResource(String path) {
        try {
            var resource = new ClassPathResource(BASE_PATH + path + ".txt");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("提示词模板已加载: {}", path);
            return content;
        } catch (IOException e) {
            log.error("提示词模板加载失败: {}", path, e);
            throw new RuntimeException("Prompt not found: " + path, e);
        }
    }

    /** 清除所有缓存的提示词（用于热更新） */
    public void evictAll() {
        cache.invalidateAll();
    }
}
