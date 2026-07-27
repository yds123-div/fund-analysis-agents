package com.hex.fund.agent.llm;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ChatModel 工厂 - 支持 DashScope 原生模式和 OpenAI 兼容模式。
 * <p>
 * 向两个 builder 注入 Spring 容器的 {@link ObservationRegistry}，使 Spring AI 自带的 GenAI 观测
 * （模型名 / 输入输出 token / 延迟等 GenAI 语义字段）真正发出。这是 LLM 追踪生效的前提--
 * 未注入时 builder 默认使用 {@link ObservationRegistry#NOOP}，观测不会触发；业务调用点
 * （{@link LlmService} 与各 agent）因此无需任何手动埋点。
 */
@Slf4j
@Component
public class ChatModelFactory {

    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private final ObservationRegistry observationRegistry;
    private final Cache<String, ChatModel> modelCache = Caffeine.newBuilder()
            .maximumSize(20).expireAfterWrite(Duration.ofHours(1)).build();

    public ChatModelFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

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
        // 注入 ObservationRegistry：未注入时 builder 默认 NOOP，Spring AI 的 GenAI 观测不会发出。
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
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
        // 注入 ObservationRegistry：未注入时 builder 默认 NOOP，Spring AI 的 GenAI 观测不会发出。
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    /** 清除所有缓存的 ChatModel 实例 */
    public void evictAll() {
        modelCache.invalidateAll();
        log.info("所有ChatModel缓存已清除");
    }
}
