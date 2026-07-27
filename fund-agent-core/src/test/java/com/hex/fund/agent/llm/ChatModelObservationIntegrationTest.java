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
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;

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
 * 仓库第一条集成测试缝（Opik/T1，L2 LLM span 生效）。
 * <p>
 * 通过真实 {@link ChatModelFactory}（注入了 {@link ObservationRegistry}）与<b>未改动的</b>
 * {@link LlmService} 调用一次 LLM，断言 Spring AI 自带的 GenAI observation 真正发出且关键属性
 * （模型名 / 输入输出 token / 延迟等 GenAI 语义字段）正确。LLM 用内嵌 HTTP 桩返 canned OpenAI
 * 兼容响应；observation 用内存 {@link ObservationHandler} 捕获，不起 Opik、不做 OTLP 传输。
 * <p>
 * 这是 RGR 的 "Red"：若 {@code ChatModelFactory} 不把 {@code ObservationRegistry} 传给 builder，
 * builder 会回落到 {@link ObservationRegistry#NOOP}，观测不触发，本测试的断言会失败。
 * <p>
 * 注：数据源管理器不在 LLM 调用路径上，本缝不涉及；编排入口处的数据源桩 + 整树断言由 T2/T3 扩展。
 */
class ChatModelObservationIntegrationTest {

    private static final String MODEL_ID = "deepseek-chat";
    private static final int INPUT_TOKENS = 10;
    private static final int OUTPUT_TOKENS = 3;

    /** canned OpenAI 兼容 chat completion 响应（含 model 与 usage token）。 */
    private static final String CANNED_RESPONSE = """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "deepseek-chat",
              "choices": [
                {"index": 0, "message": {"role": "assistant", "content": "OK"}, "finish_reason": "stop"}
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 3, "total_tokens": 13}
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
        stubServer.createContext("/", new StubChatCompletionsHandler());
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
    void llmCallEmitsGenAiObservationWithModelTokensAndLatency() {
        // 走真实 ChatModelFactory + 未改动的 LlmService 业务调用点
        LlmService.LlmResult result = llmService.chat(
                "openai", "http://127.0.0.1:" + port, "test-key", MODEL_ID,
                "You are a helpful assistant.", "Say 'OK' in one word.");

        // 业务路径照常完成
        assertThat(result.content()).containsIgnoringCase("ok");
        ModelTrace trace = result.trace();
        assertThat(trace.modelId()).isEqualTo(MODEL_ID);
        assertThat(trace.inputTokens()).isEqualTo(INPUT_TOKENS);
        assertThat(trace.outputTokens()).isEqualTo(OUTPUT_TOKENS);

        // GenAI observation 真正发出
        ChatModelObservationContext ctx = handler.lastChatContext();
        assertThat(ctx).as("ChatModel GenAI observation should have been emitted").isNotNull();
        assertThat(ctx.getName())
                .as("observation name is the GenAI chat-model convention name")
                .isEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME);

        // 关键属性经类型化上下文读取（保证取值正确）
        assertThat(ctx.getResponse().getMetadata().getModel()).isEqualTo(MODEL_ID);
        assertThat(ctx.getResponse().getMetadata().getUsage().getPromptTokens()).isEqualTo(INPUT_TOKENS);
        assertThat(ctx.getResponse().getMetadata().getUsage().getCompletionTokens()).isEqualTo(OUTPUT_TOKENS);

        // GenAI 语义字段（gen_ai.*）作为 keyValue 落入 observation--这是导出到 Opik 的字段
        Map<String, String> kvs = keyValuesAsMap(ctx);
        assertThat(kvs)
                .as("GenAI keyValues (model name + tokens)")
                .containsEntry(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), MODEL_ID)
                .containsEntry(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(), String.valueOf(INPUT_TOKENS))
                .containsEntry(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(), String.valueOf(OUTPUT_TOKENS));

        // 延迟：observation 起止被记录（start -> stop，导出后即为 span 时长）
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

    /** 内嵌 HTTP 桩：对任意请求返回 canned OpenAI 兼容 chat completion 响应。 */
    private static final class StubChatCompletionsHandler implements HttpHandler {
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
