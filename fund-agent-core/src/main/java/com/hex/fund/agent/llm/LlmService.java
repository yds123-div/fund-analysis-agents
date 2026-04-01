package com.hex.fund.agent.llm;

import com.hex.fund.agent.model.ModelTrace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一 LLM 调用服务 — 封装调用追踪与异常处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final ChatModelFactory chatModelFactory;

    /** 调用 LLM 并返回响应文本及追踪信息 */
    public LlmResult chat(String providerType, String baseUrl, String apiKey,
                          String modelId, String systemPrompt, String userPrompt) {
        ChatModel model = chatModelFactory.getOrCreate(providerType, baseUrl, apiKey, modelId);
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = model.call(
                    new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
            long latency = System.currentTimeMillis() - start;
            var result = response.getResult();
            String content = result != null ? result.getOutput().getText() : "";
            var usage = response.getMetadata().getUsage();
            ModelTrace trace = new ModelTrace(providerType, modelId, null,
                    (int) usage.getPromptTokens(), (int) usage.getCompletionTokens(), latency);
            log.debug("LLM调用完成: 模型={}, Token={}/{}, 耗时={}ms",
                    modelId, trace.inputTokens(), trace.outputTokens(), latency);
            return new LlmResult(content, trace);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("LLM调用失败: 模型={}, 耗时={}ms, 原因={}", modelId, latency, e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /** 连通性测试 — 发送最小化 prompt */
    public LlmResult testConnectivity(String providerType, String baseUrl, String apiKey, String modelId) {
        return chat(providerType, baseUrl, apiKey, modelId,
                "You are a helpful assistant.", "Say 'OK' in one word.");
    }

    public record LlmResult(String content, ModelTrace trace) {
    }
}
