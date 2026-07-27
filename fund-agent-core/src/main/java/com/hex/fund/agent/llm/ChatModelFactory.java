package com.hex.fund.agent.llm;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ChatModel 工厂 — 支持 DashScope 原生模式和 OpenAI 兼容模式。
 */
@Slf4j
@Component
public class ChatModelFactory {

    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private final Cache<String, ChatModel> modelCache = Caffeine.newBuilder()
            .maximumSize(20).expireAfterWrite(Duration.ofHours(1)).build();

    /** 获取或创建指定配置的 ChatModel */
    public ChatModel getOrCreate(String providerType, String baseUrl, String apiKey, String modelId) {
        String cacheKey = providerType + "|" + baseUrl + "|" + apiKey.hashCode() + "|" + modelId;
        return modelCache.get(cacheKey, k -> create(providerType, baseUrl, apiKey, modelId));
    }

    private ChatModel create(String providerType, String baseUrl, String apiKey, String modelId) {
        log.info("创建ChatModel实例: 类型={}, 地址={}, 模型={}", providerType, baseUrl, modelId);
        if ("dashscope".equals(providerType)) return createDashScope(baseUrl, apiKey, modelId);
        return createOpenAiCompatible(baseUrl, apiKey, modelId);
    }

    private DashScopeChatModel createDashScope(String baseUrl, String apiKey, String modelId) {
        DashScopeApi api = DashScopeApi.builder()
                .baseUrl(baseUrl != null ? baseUrl : DASHSCOPE_BASE_URL)
                .apiKey(apiKey).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelId).withTemperature(0.7).build();
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
    }

    private OpenAiChatModel createOpenAiCompatible(String baseUrl, String apiKey, String modelId) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey);
        // GLM 使用 /v4/chat/completions 而非 /v1/chat/completions
        if (baseUrl != null && baseUrl.contains("bigmodel.cn")) {
            apiBuilder.completionsPath("/v4/chat/completions");
        }
        // 火山方舟 Ark：base_url 形如 https://ark.cn-beijing.volces.com/api/v3，端点为 /chat/completions
        if (baseUrl != null && baseUrl.contains("volces.com")) {
            apiBuilder.completionsPath("/chat/completions");
        }
        OpenAiApi api = apiBuilder.build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelId).temperature(0.7).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    /** 清除所有缓存的 ChatModel 实例 */
    public void evictAll() {
        modelCache.invalidateAll();
        log.info("所有ChatModel缓存已清除");
    }
}
