package com.hex.fund.agent.orchestrator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hex.fund.agent.graph.AnalysisGraphBuilder;
import com.hex.fund.agent.llm.ChatModelFactory;
import com.hex.fund.agent.llm.LlmService;
import com.hex.fund.agent.prompt.PromptLoader;
import com.hex.fund.common.enums.ReportType;
import com.hex.fund.common.progress.TaskProgressHolder;
import com.hex.fund.datasource.core.DataSourceAdapter;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.datasource.model.NavData;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opik 端到端冒烟测试（#7）- 对真实运行的自托管 Opik 跑一次完整基金分析，查 Opik REST API 确认整棵嵌套
 * trace 真的落进看板。
 * <p>
 * 与 {@link AnalysisOrchestrationObservationIntegrationTest} 同套真实编排（{@link AnalysisOrchestrator} +
 * {@link AnalysisGraphBuilder}，节点经 {@code TracedNodeAction} 装饰、入口包根 observation；真实
 * {@link ChatModelFactory} 注入 {@link ObservationRegistry}；canned 数据源），但 observation 不再用内存捕获，
 * 而是经 {@code micrometer-tracing-bridge-otel} 桥（{@link DefaultTracingObservationHandler} + {@link OtelTracer}）
 * 把每条 observation 转成 OTel span，再由 {@link OtlpHttpSpanExporter} 经 OTLP HTTP 真正推送到本机自托管 Opik
 * （端点 {@code /api/v1/private/otel/v1/traces}，{@code projectName} header 路由到独立 project）。
 * <p>
 * 两条 LLM 路径各跑一次完整分析：DeepSeek（OpenAI 兼容，{@code providerType=openai}）与 DashScope（原生，
 * {@code providerType=dashscope}）；LLM 用内嵌 HTTP 桩返 canned 响应（OpenAI 兼容 / 原生 DashScope 各一），
 * 数据源用 canned 桩。每次跑完 flush 后查 Opik REST API 取该 trace 的完整 span 树，断言：
 * <ul>
 *   <li>根 span「基金分析」（parent 为空）携带 fundCode/fundName/batchNo/providerType/modelId 业务属性；</li>
 *   <li>根下挂 7 个图节点 span（data_collection / parallel_analysis / debate / trader / risk_manager /
 *       portfolio_advisor / report_generator）；</li>
 *   <li>parallel_analysis 下挂 6 个分析师 span（fund/technical/industry/manager/sentiment/news_analyst）；</li>
 *   <li>每个分析师 span 下各挂 LLM GenAI span（type=llm），含 model / token（usage） / 延迟（duration）。</li>
 * </ul>
 * <p>
 * 默认不进快循环：需显式 {@code OPIK_SMOKE=true} 触发（@EnabledIfEnvironmentVariable 门控，普通 {@code mvn test}
 * 跳过）；运行时若 Opik 不可达再 {@code assumeTrue} 跳过。Opik 落库异步，查询按 batchNo 轮询等待。
 */
@EnabledIfEnvironmentVariable(named = "OPIK_SMOKE", matches = "true")
class OpikEndToEndSmokeTest {

    private static final String OPIK_BASE =
            System.getenv().getOrDefault("OPIK_BASE_URL", "http://localhost:5173");
    private static final String OPIK_OTLP_ENDPOINT = OPIK_BASE + "/api/v1/private/otel/v1/traces";
    private static final String OPIK_REST = OPIK_BASE + "/api/v1/private";
    private static final String PROJECT_NAME = "fund-analysis-opik-smoke";

    private static final String FUND_CODE = "000001";
    private static final String FUND_NAME = "测试基金";

    /** canned OpenAI 兼容 chat completion 响应（DeepSeek 路径，含 model 与 usage token）。 */
    private static final String OPENAI_RESPONSE = """
            {
              "id": "chatcmpl-opik-smoke",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "deepseek-chat",
              "choices": [{"index": 0, "message": {"role": "assistant", "content": "OK"}, "finish_reason": "stop"}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 3, "total_tokens": 13}
            }
            """;

    /** canned 原生 DashScope chat 响应（DashScope 路径，snake_case request_id/output/usage，无 model 字段）。 */
    private static final String DASHSCOPE_RESPONSE = """
            {
              "request_id": "req-opik-smoke",
              "output": {"text": "OK", "choices": [{"message": {"role": "assistant", "content": "OK"}, "finish_reason": "stop"}]},
              "usage": {"input_tokens": 10, "output_tokens": 3, "total_tokens": 13}
            }
            """;

    private HttpServer openAiStub;
    private HttpServer dashScopeStub;
    private OpenTelemetrySdk openTelemetry;
    private ObservationRegistry registry;
    private AnalysisOrchestrator orchestrator;
    private CountingHandler localCapture;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        assumeTrue(opikReachable(), "Opik must be running at " + OPIK_BASE
                + " (set OPIK_SMOKE=true and start self-hosted Opik)");

        openAiStub = startStub(new FixedBodyHandler(OPENAI_RESPONSE));
        dashScopeStub = startStub(new FixedBodyHandler(DASHSCOPE_RESPONSE));

        // OTLP HTTP 导出器 -> 本机自托管 Opik；projectName header 把 trace 路由到独立 project（自动创建）。
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(OPIK_OTLP_ENDPOINT)
                .addHeader("projectName", PROJECT_NAME)
                .setTimeout(Duration.ofSeconds(10))
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

        // Micrometer Observation -> OTel span 桥（与生产 Spring Boot autoconfig 同套核心件）。
        OtelTracer tracer = new OtelTracer(
                openTelemetry.getTracer("io.micrometer.tracing.otel.bridge"),
                new OtelCurrentTraceContext(),
                event -> { }); // EventPublisher no-op
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
        // 本地计数（仅诊断：确认 observation 真的发出；不替代对 Opik 的断言）。
        localCapture = new CountingHandler();
        registry.observationConfig().observationHandler(localCapture);

        ChatModelFactory factory = new ChatModelFactory(registry);
        LlmService llmService = new LlmService(factory);
        PromptLoader promptLoader = new PromptLoader();
        TaskProgressHolder progressHolder = new TaskProgressHolder();
        DataSourceManager dataSourceManager = stubDataSourceManager();
        AnalysisGraphBuilder graphBuilder = new AnalysisGraphBuilder(
                dataSourceManager, llmService, promptLoader, progressHolder, registry);
        orchestrator = new AnalysisOrchestrator(graphBuilder, registry);
        orchestrator.configure(1); // 1 轮辩论，足以体现完整树（7 节点 + 6 分析师 + 各自 LLM）

        httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterEach
    void tearDown() {
        if (openTelemetry != null) {
            openTelemetry.getSdkTracerProvider().shutdown(); // flush 残留 span 到 Opik
        }
        if (openAiStub != null) openAiStub.stop(0);
        if (dashScopeStub != null) dashScopeStub.stop(0);
    }

    @Test
    void deepSeekPathTraceLandsInOpikWithNestedTree() {
        String batchNo = runAnalysis("openai", openAiStub.getAddress().getPort(), "deepseek-chat");
        List<OpikSpan> spans = pollOpikSpansForBatch(batchNo);
        assertNestedTrace(spans, "deepseek-chat", "openai", batchNo);
    }

    @Test
    void dashScopePathTraceLandsInOpikWithNestedTree() {
        String batchNo = runAnalysis("dashscope", dashScopeStub.getAddress().getPort(), "qwen-plus");
        List<OpikSpan> spans = pollOpikSpansForBatch(batchNo);
        assertNestedTrace(spans, "qwen-plus", "dashscope", batchNo);
    }

    // ----------------------------- 编排 + 查询 -----------------------------

    /** 跑一次完整分析并 flush OTLP 导出。 */
    private String runAnalysis(String providerType, int stubPort, String modelId) {
        AnalysisOrchestrator.AnalysisResult result = orchestrator.analyze(
                FUND_CODE, FUND_NAME, ReportType.DAILY, providerType,
                "http://127.0.0.1:" + stubPort, "test-key", modelId);
        openTelemetry.getSdkTracerProvider().forceFlush().join(15, java.util.concurrent.TimeUnit.SECONDS); // 同步推送到 Opik
        // localCapture.total 仅为诊断（Micrometer 多 handler 触发语义不定），真实断言在 Opik 侧。
        return result.batchNo();
    }

    /** 轮询 Opik：直到按 batchNo 找到根 span 的 trace，返回该 trace 的全部 span。 */
    private List<OpikSpan> pollOpikSpansForBatch(String batchNo) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        List<OpikSpan> all = List.of();
        while (System.nanoTime() < deadline) {
            all = fetchProjectSpans();
            if (findRootSpanForBatch(all, batchNo) != null) break;
            sleep(1000);
        }
        OpikSpan root = findRootSpanForBatch(all, batchNo);
        if (root == null) {
            fail("Opik trace for batchNo=%s not found within 30s (project=%s, spansSeen=%d, localObs=%d)"
                    .formatted(batchNo, PROJECT_NAME, all.size(), localCapture.total));
        }
        String traceId = root.traceId;
        return all.stream().filter(s -> traceId.equals(s.traceId)).toList();
    }

    /** 取 project 下全部 span（project_name 直查，无需先解析 project id）。 */
    private List<OpikSpan> fetchProjectSpans() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OPIK_REST + "/spans?project_name=" + urlEncode(PROJECT_NAME)
                            + "&page=1&size=1000"))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();
            JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray content = body.has("content") ? body.getAsJsonArray("content") : new JsonArray();
            List<OpikSpan> spans = new ArrayList<>();
            for (JsonElement e : content) spans.add(OpikSpan.from(e.getAsJsonObject()));
            return spans;
        } catch (Exception e) {
            return List.of();
        }
    }

    private OpikSpan findRootSpanForBatch(List<OpikSpan> spans, String batchNo) {
        for (OpikSpan s : spans) {
            if (!AnalysisOrchestrator.ROOT_OBSERVATION_NAME.equals(s.name)) continue;
            if (s.parentSpanId != null && !s.parentSpanId.isBlank()) continue; // 根 span 无父
            if (s.raw.contains("\"batchNo\":\"" + batchNo + "\"")
                    || s.raw.contains("\"batchNo\": \"" + batchNo + "\"")) return s;
        }
        return null;
    }

    // ----------------------------- 断言 -----------------------------

    private void assertNestedTrace(List<OpikSpan> spans, String modelId, String providerType, String batchNo) {
        assertThat(spans).as("trace should contain spans").isNotEmpty();

        OpikSpan root = spans.stream()
                .filter(s -> AnalysisOrchestrator.ROOT_OBSERVATION_NAME.equals(s.name)
                        && (s.parentSpanId == null || s.parentSpanId.isBlank()))
                .findFirst().orElseThrow(() -> new AssertionError("root span「基金分析」not found"));
        assertThat(root.raw).as("root span carries fundCode").contains("\"fundCode\":\"" + FUND_CODE + "\"");
        assertThat(root.raw).as("root span carries fundName").contains("\"fundName\":\"" + FUND_NAME + "\"");
        assertThat(root.raw).as("root span carries batchNo").contains(batchNo);
        assertThat(root.raw).as("root span carries providerType").contains("\"providerType\":\"" + providerType + "\"");
        assertThat(root.raw).as("root span carries modelId").contains("\"modelId\":\"" + modelId + "\"");

        // L0 -> L1：7 个图节点 span
        List<OpikSpan> nodeSpans = childrenOf(root, spans);
        assertThat(nodeSpans).as("root has 7 node child spans").hasSize(7);
        assertThat(nodeSpans.stream().map(s -> s.name).toList())
                .containsExactlyInAnyOrder(
                        "data_collection", "parallel_analysis", "debate",
                        "trader", "risk_manager", "portfolio_advisor", "report_generator");

        // L1 -> L2：parallel_analysis 下 6 个分析师 span
        OpikSpan parallel = nodeSpans.stream().filter(s -> "parallel_analysis".equals(s.name))
                .findFirst().orElseThrow(() -> new AssertionError("parallel_analysis node not found"));
        List<OpikSpan> analystSpans = childrenOf(parallel, spans);
        assertThat(analystSpans).as("parallel_analysis has 6 analyst child spans").hasSize(6);
        assertThat(analystSpans.stream().map(s -> s.name).toList())
                .containsExactlyInAnyOrder(
                        "fund_analyst", "technical_analyst", "industry_analyst",
                        "manager_analyst", "sentiment_analyst", "news_analyst");

        // L2 -> L3：每个分析师下挂 LLM GenAI span（type=llm，含 model/token/延迟）
        for (OpikSpan analyst : analystSpans) {
            List<OpikSpan> llmChildren = childrenOf(analyst, spans).stream()
                    .filter(s -> "llm".equals(s.type)).toList();
            assertThat(llmChildren).as("analyst '%s' has >=1 LLM child span", analyst.name).isNotEmpty();
            for (OpikSpan llm : llmChildren) {
                assertThat(llm.model).as("LLM span '%s' has model name", analyst.name).isNotBlank();
                assertThat(llm.totalTokens).as("LLM span '%s' has total tokens > 0", analyst.name).isGreaterThan(0);
                assertThat(llm.durationMs).as("LLM span '%s' has duration > 0", analyst.name).isGreaterThan(0.0);
            }
        }
    }

    private List<OpikSpan> childrenOf(OpikSpan parent, List<OpikSpan> all) {
        return all.stream().filter(s -> parent.id.equals(s.parentSpanId)).toList();
    }

    // ----------------------------- Opik 连通性 -----------------------------

    private boolean opikReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OPIK_REST + "/projects?page=1&size=1"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = httpClientSend(req);
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<String> httpClientSend(HttpRequest req) throws Exception {
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ----------------------------- 桩 -----------------------------

    private HttpServer startStub(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private DataSourceManager stubDataSourceManager() {
        DataSourceManager manager = new DataSourceManager(null);
        manager.register(new CannedDataSourceAdapter(), 0);
        return manager;
    }

    /** 内嵌 HTTP 桩：对任意请求返回固定 body（OpenAI 兼容 / 原生 DashScope 各一）。 */
    private static final class FixedBodyHandler implements HttpHandler {
        private final String body;
        FixedBodyHandler(String body) { this.body = body; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            finally { exchange.close(); }
        }
    }

    /** canned 数据源适配器：返回预置基金数据，永不触网。 */
    private static final class CannedDataSourceAdapter implements DataSourceAdapter {
        @Override public String getSourceName() { return "canned-stub"; }
        @Override public String getSourceCode() { return "canned-stub"; }
        @Override public boolean isAvailable() { return true; }
        @Override public FundBasicData getFundBasic(String fundCode) {
            return FundBasicData.builder().fundCode(fundCode).fundName(FUND_NAME)
                    .fundType("混合型").source("canned-stub").build();
        }
        @Override public List<NavData> getNavHistory(String fundCode, LocalDate start, LocalDate end) {
            return List.of(
                    NavData.builder().fundCode(fundCode).navDate(end.minusDays(1))
                            .unitNav(new BigDecimal("1.2345")).accumulatedNav(new BigDecimal("2.0000"))
                            .dayGrowthRate(new BigDecimal("0.50")).source("canned-stub").build(),
                    NavData.builder().fundCode(fundCode).navDate(end)
                            .unitNav(new BigDecimal("1.2407")).accumulatedNav(new BigDecimal("2.0100"))
                            .dayGrowthRate(new BigDecimal("0.50")).source("canned-stub").build());
        }
        @Override public NavData getLatestNav(String fundCode) { return null; }
        @Override public com.hex.fund.datasource.model.FundEstimate getRealTimeEstimate(String fundCode) { return null; }
        @Override public List<com.hex.fund.datasource.model.HoldingData> getFundHoldings(String fundCode, String reportDate) { return List.of(); }
        @Override public com.hex.fund.datasource.model.FundManagerData getFundManager(String fundCode) { return null; }
        @Override public List<FundBasicData> searchFunds(String keyword) { return List.of(); }
    }

    /** 诊断用：只计数 observation 起止（确认编排确实发出 observation，不替代 Opik 断言）。 */
    private static final class CountingHandler implements ObservationHandler<Observation.Context> {
        int total;
        @Override public void onStop(Observation.Context context) { total++; }
        @Override public boolean supportsContext(Observation.Context context) { return true; }
    }

    // ----------------------------- Opik JSON 模型 -----------------------------

    private record OpikSpan(String id, String traceId, String parentSpanId, String name,
                            String type, String model, long totalTokens, double durationMs, String raw) {
        static OpikSpan from(JsonObject o) {
            return new OpikSpan(
                    str(o, "id"),
                    str(o, "trace_id"),
                    str(o, "parent_span_id"),
                    str(o, "name"),
                    str(o, "type"),
                    str(o, "model"),
                    usageTotal(o),
                    o.has("duration") && o.get("duration").isJsonPrimitive()
                            ? o.get("duration").getAsDouble() : 0.0,
                    o.toString());
        }
        private static String str(JsonObject o, String k) {
            return o.has(k) && o.get(k).isJsonPrimitive() && !o.get(k).isJsonNull()
                    ? o.get(k).getAsString() : null;
        }
        private static long usageTotal(JsonObject o) {
            if (!o.has("usage") || !o.get("usage").isJsonObject()) return 0L;
            JsonObject u = o.getAsJsonObject("usage");
            String[] keys = {"total_tokens", "completion_tokens", "prompt_tokens"};
            for (String k : keys) {
                if (u.has(k) && u.get(k).isJsonPrimitive()) return u.get(k).getAsLong();
            }
            return 0L;
        }
    }

    // ----------------------------- 小工具 -----------------------------

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
