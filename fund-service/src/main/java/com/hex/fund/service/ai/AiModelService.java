package com.hex.fund.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.agent.llm.ChatModelFactory;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.common.util.CryptoUtil;
import com.hex.fund.service.entity.AgentModelBinding;
import com.hex.fund.service.entity.AiProviderConfig;
import com.hex.fund.service.mapper.AgentModelBindingMapper;
import com.hex.fund.service.mapper.AiProviderConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库驱动的 AI 模型管理服务。
 * 从 DB 读取 Provider 配置，解密 API Key，提供 ChatModel 实例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiProviderConfigMapper providerMapper;
    private final AgentModelBindingMapper bindingMapper;
    private final ChatModelFactory chatModelFactory;
    private final LlmService llmService;
    private final Map<String, AiProviderConfig> providerCache = new ConcurrentHashMap<>();

    /** 根据 Agent 和思考级别获取 ChatModel */
    public ChatModel getModelForAgent(String agentId, String thinkLevel) {
        AgentModelBinding binding = findBinding(agentId, thinkLevel);
        return getModelByProvider(binding.getProviderCode(), binding.getModelId());
    }

    /** 根据 Provider 编码和模型 ID 获取 ChatModel */
    public ChatModel getModelByProvider(String providerCode, String modelId) {
        AiProviderConfig config = getProviderConfig(providerCode);
        String apiKey = CryptoUtil.decrypt(config.getApiKeyEncrypted());
        return chatModelFactory.getOrCreate(resolveProviderType(config), config.getBaseUrl(), apiKey, modelId);
    }

    /** 解析 Provider 连接信息 */
    public ProviderInfo resolveProvider(String providerCode) {
        AiProviderConfig config = getProviderConfig(providerCode);
        String apiKey = CryptoUtil.decrypt(config.getApiKeyEncrypted());
        return new ProviderInfo(resolveProviderType(config), config.getBaseUrl(), apiKey);
    }

    /** 解析 Agent 绑定的完整 Provider 信息 */
    public AgentProviderInfo resolveAgentProvider(String agentId, String thinkLevel) {
        AgentModelBinding binding = findBinding(agentId, thinkLevel);
        ProviderInfo provider = resolveProvider(binding.getProviderCode());
        return new AgentProviderInfo(provider.type(), provider.baseUrl(), provider.apiKey(), binding.getModelId());
    }

    /** 测试 Provider 连通性 */
    public String testConnectivity(String providerCode) {
        AiProviderConfig config = getProviderConfig(providerCode);
        String apiKey = CryptoUtil.decrypt(config.getApiKeyEncrypted());
        String modelId = getDefaultModelForProvider(providerCode);
        try {
            LlmService.LlmResult result = llmService.testConnectivity(
                    resolveProviderType(config), config.getBaseUrl(), apiKey, modelId);
            updateConnectivityStatus(config, "ONLINE");
            return result.content();
        } catch (Exception e) {
            updateConnectivityStatus(config, "ERROR");
            throw e;
        }
    }

    public List<AiProviderConfig> listEnabledProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getEnabled, 1));
    }

    public List<AiProviderConfig> listAllProviders() {
        return providerMapper.selectList(null);
    }

    public List<AgentModelBinding> listBindings() {
        return bindingMapper.selectList(null);
    }

    /** 保存或更新 Provider 配置，原始 API Key 自动加密 */
    public void saveProvider(AiProviderConfig config) {
        encryptApiKeyIfNeeded(config);
        if (config.getId() != null) providerMapper.updateById(config);
        else providerMapper.insert(config);
        providerCache.remove(config.getProviderCode());
        chatModelFactory.evictAll();
    }

    public void saveBinding(AgentModelBinding binding) {
        if (binding.getId() != null) bindingMapper.updateById(binding);
        else bindingMapper.insert(binding);
    }

    public AiProviderConfig getProviderConfig(String providerCode) {
        return providerCache.computeIfAbsent(providerCode, code -> {
            AiProviderConfig config = providerMapper.selectOne(
                    new LambdaQueryWrapper<AiProviderConfig>()
                            .eq(AiProviderConfig::getProviderCode, code)
                            .eq(AiProviderConfig::getEnabled, 1));
            if (config == null) throw new RuntimeException("AI provider not found or disabled: " + code);
            return config;
        });
    }

    // ---- private helpers ----

    private String resolveProviderType(AiProviderConfig config) {
        return config.getProviderType() != null ? config.getProviderType() : "openai";
    }

    private AgentModelBinding findBinding(String agentId, String thinkLevel) {
        AgentModelBinding binding = queryBinding(agentId, thinkLevel);
        if (binding == null) binding = queryBinding("default", thinkLevel);
        if (binding == null) throw new RuntimeException("No model binding: agent=" + agentId + ", level=" + thinkLevel);
        return binding;
    }

    private AgentModelBinding queryBinding(String agentId, String thinkLevel) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<AgentModelBinding>()
                .eq(AgentModelBinding::getAgentId, agentId)
                .eq(AgentModelBinding::getThinkLevel, thinkLevel)
                .eq(AgentModelBinding::getEnabled, 1));
    }

    private void encryptApiKeyIfNeeded(AiProviderConfig config) {
        if (config.getApiKeyEncrypted() == null || config.getApiKeyEncrypted().isEmpty()) return;
        try {
            CryptoUtil.decrypt(config.getApiKeyEncrypted());
        } catch (Exception e) {
            config.setApiKeyEncrypted(CryptoUtil.encrypt(config.getApiKeyEncrypted()));
        }
    }

    private void updateConnectivityStatus(AiProviderConfig config, String status) {
        config.setConnectivityStatus(status);
        config.setLastCheckTime(LocalDateTime.now());
        providerMapper.updateById(config);
        providerCache.put(config.getProviderCode(), config);
    }

    private String getDefaultModelForProvider(String providerCode) {
        AgentModelBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<AgentModelBinding>()
                        .eq(AgentModelBinding::getProviderCode, providerCode)
                        .eq(AgentModelBinding::getEnabled, 1).last("LIMIT 1"));
        return binding != null ? binding.getModelId() : switch (providerCode) {
            case "deepseek" -> "deepseek-chat";
            case "zhipu" -> "glm-4-flash";
            case "dashscope" -> "qwen-plus";
            default -> "gpt-4o-mini";
        };
    }

    public record ProviderInfo(String type, String baseUrl, String apiKey) {
    }

    public record AgentProviderInfo(String type, String baseUrl, String apiKey,
                                    String modelId) {
    }
}

