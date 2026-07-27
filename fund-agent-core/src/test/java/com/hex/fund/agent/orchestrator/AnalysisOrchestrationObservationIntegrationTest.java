package com.hex.fund.agent.orchestrator;

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
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2 主缝 - 在分析编排入口验证一次完整基金分析跑完后产生正确嵌套的 observation 树。
 * <p>
 * 走真实 {@link AnalysisOrchestrator} + {@link AnalysisGraphBuilder}（节点经 {@code TracedNodeAction}
 * 装饰、入口包根 observation），真实 {@link ChatModelFactory}（注入了 {@link ObservationRegistry}）与
 * 未改动的 {@link LlmService}；桩掉数据源管理器（返 canned 数据，数据采集节点不依赖真数据源）与 LLM
 * （内嵌 HTTP 桩返 canned OpenAI 兼容响应，触发 Spring AI GenAI observation）。用内存
 * {@link ObservationHandler} 捕获全部 observation，不起 Opik、不做 OTLP 传输。
 * <p>
 * 断言捕获的 observation 树：根「基金分析」（携带 fundCode/fundName/batchNo/providerType/modelId 业务属性）
 * -> 7 个图节点 span -> 节点内 LLM GenAI span 正确嵌套；辩论节点多轮 LLM span 体现。
 * <p>
 * RGR：若不在图构建处用 {@code TracedNodeAction} 包裹各节点、或不在编排入口包根 observation，
 * 节点 span 与根 span 将不存在或父子关系断裂，本测试断言失败。
 * <p>
 * 注：并行分析师节点的虚拟线程上下文传播（6 个分析师 span 正确挂在 parallel_analysis 节点下）属 T3（#6）；
 * 当前阶段其 6 个 LLM GenAI span 因虚拟线程不继承 ThreadLocal 观测上下文而处于顶层（parent=null），故本测试
 * 仅断言 parallel_analysis 节点 span 存在，其 LLM span 嵌套由 #6 接续。
 */
class AnalysisOrchestrationObservationIntegrationTest {

    private static final String FUND_CODE = "000001";
    private static final String FUND_NAME = "测试基金";
    private static final String PROVIDER_TYPE = "openai";
    private static final String MODEL_ID = "deepseek-chat";

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
    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/", new StubChatCompletionsHandler());
        stubServer.start();
        port = stubServer.getAddress().getPort();

        registry = ObservationRegistry.create();
        handler = new CapturingHandler();
        registry.observationConfig().observationHandler(handler);

        ChatModelFactory factory = new ChatModelFactory(registry);
        LlmService llmService = new LlmService(factory);
        PromptLoader promptLoader = new PromptLoader();
        TaskProgressHolder progressHolder = new TaskProgressHolder();
        DataSourceManager dataSourceManager = stubDataSourceManager();

        AnalysisGraphBuilder graphBuilder = new AnalysisGraphBuilder(
                dataSourceManager, llmService, promptLoader, progressHolder, registry);
        orchestrator = new AnalysisOrchestrator(graphBuilder, registry);
        // 两轮辩论，体现多轮 LLM span（2 轮 × 2 研究员 + 1 综合 = 5 个 LLM span）。
        orchestrator.configure(2);
    }

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    @Test
    void analysisEmitsNestedObservationTreeRootNodesAndLlmSpans() {
        orchestrator.analyze(FUND_CODE, FUND_NAME, ReportType.DAILY, PROVIDER_TYPE,
                "http://127.0.0.1:" + port, "test-key", MODEL_ID);

        List<CapturedObservation> all = handler.captured;
        assertThat(all).as("observations should have been captured").isNotEmpty();

        // --- L0：根 observation「基金分析」，无父且非 GenAI ---
        // （6 个并行分析师 GenAI span 也无父，但它们是 GenAI、属 #6 待修的孤儿 span，非根。）
        List<CapturedObservation> roots = all.stream()
                .filter(o -> o.parentName == null && !o.isChatModel).toList();
        assertThat(roots).as("exactly one root observation with no parent").hasSize(1);
        CapturedObservation root = roots.get(0);
        assertThat(root.name).isEqualTo(AnalysisOrchestrator.ROOT_OBSERVATION_NAME);
        assertThat(root.isChatModel).isFalse();

        // 根携带业务属性：fundCode/providerType/modelId（低基数）+ fundName/batchNo（高基数）
        assertThat(root.keyValues)
                .containsEntry("fundCode", FUND_CODE)
                .containsEntry("providerType", PROVIDER_TYPE)
                .containsEntry("modelId", MODEL_ID)
                .containsEntry("fundName", FUND_NAME)
                .containsEntry("batchNo", orchestratorBatchNo(all));
        assertThat(root.keyValues.get("batchNo")).as("batchNo is a non-empty id").isNotBlank();

        // --- L1：7 个图节点 span，均为根的子 span ---
        List<CapturedObservation> nodeSpans = childrenOf(root.name, all);
        assertThat(nodeSpans).as("root has 7 node child spans").hasSize(7);
        assertThat(nodeSpans.stream().map(o -> o.name).toList())
                .containsExactlyInAnyOrder(
                        "data_collection", "parallel_analysis", "debate",
                        "trader", "risk_manager", "portfolio_advisor", "report_generator");
        nodeSpans.forEach(n -> assertThat(n.isChatModel)
                .as("node span '%s' is not itself a GenAI span", n.name).isFalse());

        // --- L2：节点内 LLM GenAI span 正确嵌套在所属节点 span 下 ---
        // 数据采集节点不调用 LLM，无 GenAI 子 span。
        assertThat(genAiChildrenOf("data_collection", all))
                .as("data_collection node makes no LLM calls").isEmpty();
        // 5 个同线程 LLM 节点：辩论（多轮）+ 交易/风控/组合/报告（各一）。
        assertThat(genAiChildrenOf("debate", all))
                .as("debate node emits multi-round LLM spans (2 rounds x 2 researchers + 1 synthesis = 5)")
                .hasSize(5);
        assertThat(genAiChildrenOf("trader", all)).as("trader node emits 1 LLM span").hasSize(1);
        assertThat(genAiChildrenOf("risk_manager", all)).as("risk_manager node emits 1 LLM span").hasSize(1);
        assertThat(genAiChildrenOf("portfolio_advisor", all)).as("portfolio_advisor node emits 1 LLM span").hasSize(1);
        assertThat(genAiChildrenOf("report_generator", all)).as("report_generator node emits 1 LLM span").hasSize(1);
        // 嵌套 LLM span 的模型名属性正确（GenAI 语义字段）
        genAiChildrenOf("trader", all).forEach(g ->
                assertThat(g.keyValues).containsEntry(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), MODEL_ID));

        // 并行分析节点 span 存在；其 LLM span 嵌套属 T3（#6），当前阶段为顶层孤儿 span。
        assertThat(genAiChildrenOf("parallel_analysis", all))
                .as("parallel_analysis node span exists; its analyst LLM nesting is T3 (#6)")
                .isEmpty();

        // 6 个并行分析师 LLM span 确实发出（虚拟线程下 parent=null），证明分析管线端到端完成。
        List<CapturedObservation> orphanedGenAi = all.stream()
                .filter(o -> o.isChatModel && o.parentName == null).toList();
        assertThat(orphanedGenAi).as("6 parallel analyst LLM spans emitted (orphaned until #6)").hasSize(6);
    }

    /** 从捕获列表里取根 observation 的 batchNo（由编排入口写入）。 */
    private String orchestratorBatchNo(List<CapturedObservation> all) {
        return all.stream()
                .filter(o -> AnalysisOrchestrator.ROOT_OBSERVATION_NAME.equals(o.name))
                .map(o -> o.keyValues.get("batchNo"))
                .findFirst().orElse(null);
    }

    private List<CapturedObservation> childrenOf(String parentName, List<CapturedObservation> all) {
        return all.stream().filter(o -> parentName.equals(o.parentName)).toList();
    }

    private List<CapturedObservation> genAiChildrenOf(String parentName, List<CapturedObservation> all) {
        return all.stream()
                .filter(o -> parentName.equals(o.parentName) && o.isChatModel)
                .toList();
    }

    /** 桩数据源管理器：注册一个返 canned 数据的适配器，使数据采集节点不依赖真数据源。 */
    private DataSourceManager stubDataSourceManager() {
        DataSourceManager manager = new DataSourceManager(null);
        manager.register(new CannedDataSourceAdapter(), 0);
        return manager;
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

    /** canned 数据源适配器：返回预置基金数据，永不触网。 */
    private static final class CannedDataSourceAdapter implements DataSourceAdapter {
        @Override
        public String getSourceName() { return "canned-stub"; }

        @Override
        public String getSourceCode() { return "canned-stub"; }

        @Override
        public boolean isAvailable() { return true; }

        @Override
        public FundBasicData getFundBasic(String fundCode) {
            return FundBasicData.builder().fundCode(fundCode).fundName(FUND_NAME)
                    .fundType("混合型").source("canned-stub").build();
        }

        @Override
        public List<NavData> getNavHistory(String fundCode, LocalDate start, LocalDate end) {
            return List.of(
                    NavData.builder().fundCode(fundCode).navDate(end.minusDays(1))
                            .unitNav(new BigDecimal("1.2345")).accumulatedNav(new BigDecimal("2.0000"))
                            .dayGrowthRate(new BigDecimal("0.50")).source("canned-stub").build(),
                    NavData.builder().fundCode(fundCode).navDate(end)
                            .unitNav(new BigDecimal("1.2407")).accumulatedNav(new BigDecimal("2.0100"))
                            .dayGrowthRate(new BigDecimal("0.50")).source("canned-stub").build());
        }

        @Override
        public NavData getLatestNav(String fundCode) { return null; }

        @Override
        public com.hex.fund.datasource.model.FundEstimate getRealTimeEstimate(String fundCode) { return null; }

        @Override
        public List<com.hex.fund.datasource.model.HoldingData> getFundHoldings(String fundCode, String reportDate) {
            return List.of();
        }

        @Override
        public com.hex.fund.datasource.model.FundManagerData getFundManager(String fundCode) { return null; }

        @Override
        public List<FundBasicData> searchFunds(String keyword) { return List.of(); }
    }

    /** 内存 ObservationHandler：捕获全部 observation 的名 / 父名 / 键值 / 是否 GenAI。 */
    private static final class CapturingHandler implements ObservationHandler<Observation.Context> {

        private final List<CapturedObservation> captured = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onStop(Observation.Context context) {
            String parentName = null;
            ObservationView parent = context.getParentObservation();
            if (parent != null && parent.getContextView() != null) {
                parentName = parent.getContextView().getName();
            }
            Map<String, String> kvs = new HashMap<>();
            for (KeyValue kv : context.getLowCardinalityKeyValues()) {
                kvs.put(kv.getKey(), kv.getValue());
            }
            for (KeyValue kv : context.getHighCardinalityKeyValues()) {
                kvs.put(kv.getKey(), kv.getValue());
            }
            boolean isChatModel = context instanceof ChatModelObservationContext;
            captured.add(new CapturedObservation(context.getName(), parentName, kvs, isChatModel));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true; // 捕获根 / 节点 / GenAI 全部 observation
        }
    }

    private record CapturedObservation(String name, String parentName,
                                      Map<String, String> keyValues, boolean isChatModel) {
    }
}
