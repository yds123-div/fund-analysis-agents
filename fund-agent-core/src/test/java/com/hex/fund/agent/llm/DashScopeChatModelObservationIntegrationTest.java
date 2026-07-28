package com.hex.fund.agent.llm;

import com.hex.fund.agent.model.ModelTrace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opik/T5（可选，非主缝）- DashScope 路径桩测试。
 * <p>
 * 与主缝（OpenAI 兼容路径，{@link ChatModelObservationIntegrationTest}）同模式覆盖：走同一
 * {@link ChatModelFactory}（注入了同一 {@link ObservationRegistry}）与<b>未改动的</b> {@link LlmService}，
 * 调用一次 DashScope ChatModel，断言 Spring AI 自带的 GenAI observation 真正发出且关键属性
 * （模型名 / 输入输出 token / 延迟）正确。DashScope LLM 用内嵌 HTTP 桩返 canned <b>原生 DashScope</b>
 * 响应（/api/v1/services/aigc/text-generation/generation，非 OpenAI 兼容格式）；observation 用内存
 * {@link ObservationHandler} 捕获，不起 Opik、不做 OTLP 传输、无额外埋点。
 * <p>
 * 证明换 provider（DashScope）也能观测，T1 对 DashScope builder 注入 ObservationRegistry 的修复被真实触发。
 */
class DashScopeChatModelObservationIntegrationTest {

    private static final String MODEL_ID = "qwen-plus";
    private static final int INPUT_TOKENS = 10;
    private static final int OUTPUT_TOKENS = 3;

    /** canned 原生 DashScope chat 响应（snake_case：request_id/output/usage，非 OpenAI 兼容格式）。 */
    private static final String CANNED_RESPONSE = """
            {
              "request_id": "req-test",
              "output": {
                "text": "OK",
                "choices": [
                  {"message": {"role": "assistant", "content": "OK"}, "finish_reason": "stop"}
                ]
              },
              "usage": {"input_tokens": 10, "output_tokens": 3, "total_tokens": 13}
            }
            """;

    private HttpServer stubServer;
    private int port;
    private ObservationRegistry registry;
    private CapturingHandler handler;
    private ChatModelFactory factory;
    private LlmService llmService;

    @BeforeEach
    void setUp() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/", new StubDashScopeHandler());
        stubServer.start();
        port = stubServer.getAddress().getPort();

        registry = ObservationRegistry.create();
        handler = new CapturingHandler();
        registry.observationConfig().observationHandler(handler);

        factory = new ChatModelFactory(registry);
        llmService = new LlmService(factory);
    }

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    @Test
    void dashScopeLlmCallEmitsGenAiObservationWithModelTokensAndLatency() {
        // 走真实 ChatModelFactory（DashScope 路径）+ 未改动的 LlmService 业务调用点
        LlmService.LlmResult result = llmService.chat(
                "dashscope", "http://127.0.0.1:" + port, "test-key", MODEL_ID,
                "You are a helpful assistant.", "Say 'OK' in one word.");

        // 业务路径照常完成
        assertThat(result.content()).containsIgnoringCase("ok");
        ModelTrace trace = result.trace();
        assertThat(trace.modelId()).isEqualTo(MODEL_ID);
        assertThat(trace.inputTokens()).isEqualTo(INPUT_TOKENS);
        assertThat(trace.outputTokens()).isEqualTo(OUTPUT_TOKENS);

        // GenAI observation 真正发出（DashScope ChatModel 同样走 Spring AI GenAI 观测）
        ChatModelObservationContext ctx = handler.lastChatContext();
        assertThat(ctx).as("DashScope ChatModel GenAI observation should have been emitted").isNotNull();

        // 关键属性经类型化上下文读取：DashScope 原生响应无 model 字段，token 经 usage 映射
        assertThat(ctx.getResponse().getMetadata().getUsage().getPromptTokens()).isEqualTo(INPUT_TOKENS);
        assertThat(ctx.getResponse().getMetadata().getUsage().getCompletionTokens()).isEqualTo(OUTPUT_TOKENS);

        // GenAI 语义字段（gen_ai.*）作为 keyValue 落入 observation--这是导出到 Opik 的字段。
        // DashScope 原生响应不带 model，故响应模型名空；模型名取自请求（gen_ai.request.model = 选项 modelId）。
        Map<String, String> kvs = keyValuesAsMap(ctx);
        assertThat(kvs)
                .as("GenAI keyValues (request model name + tokens)")
                .containsEntry(LowCardinalityKeyNames.REQUEST_MODEL.asString(), MODEL_ID)
                .containsEntry(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(), String.valueOf(INPUT_TOKENS))
                .containsEntry(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(), String.valueOf(OUTPUT_TOKENS));

        // 延迟：observation 起止被记录
        assertThat(handler.durationOf(ctx)).as("observation latency must be recorded")
                .isNotNull()
                .isGreaterThanOrEqualTo(Duration.ZERO);
    }

    private static Map<String, String> keyValuesAsMap(Observation.Context ctx) {
        Map<String, String> map = new HashMap<>();
        for (KeyValue kv : ctx.getLowCardinalityKeyValues()) {
            map.put(kv.getKey(), kv.getValue());
        }
        for (KeyValue kv : ctx.getHighCardinalityKeyValues()) {
            map.put(kv.getKey(), kv.getValue());
        }
        return map;
    }

    /** 内嵌 HTTP 桩：对任意请求返回 canned 原生 DashScope chat 响应。 */
    private static final class StubDashScopeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = CANNED_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } finally {
                exchange.close();
            }
        }
    }

    /** 内存 ObservationHandler：仅捕获 ChatModelObservationContext 及其起止时间（延迟）。 */
    private static final class CapturingHandler implements ObservationHandler<Observation.Context> {

        private final List<ChatModelObservationContext> chatContexts = new ArrayList<>();
        private final Map<Observation.Context, Instant> startedAt = new ConcurrentHashMap<>();
        private final Map<Observation.Context, Duration> durations = new ConcurrentHashMap<>();

        @Override
        public void onStart(Observation.Context context) {
            startedAt.put(context, Instant.now());
        }

        @Override
        public void onStop(Observation.Context context) {
            Instant start = startedAt.get(context);
            if (start != null) {
                durations.put(context, Duration.between(start, Instant.now()));
            }
            if (context instanceof ChatModelObservationContext chatCtx) {
                chatContexts.add(chatCtx);
            }
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ChatModelObservationContext;
        }

        ChatModelObservationContext lastChatContext() {
            return chatContexts.isEmpty() ? null : chatContexts.get(chatContexts.size() - 1);
        }

        Duration durationOf(Observation.Context context) {
            return durations.get(context);
        }
    }
}
