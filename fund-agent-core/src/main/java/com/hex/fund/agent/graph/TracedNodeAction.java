package com.hex.fund.agent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Map;

/**
 * 可追踪节点动作装饰器 - 包裹现有 {@link NodeAction}，为每个图节点执行产生一条 Micrometer
 * observation（span），span 名即节点身份。图编排框架本身不产生 observation，故在图构建处用本装饰器
 * 包装各节点；业务节点实现的行为不变（仅多一层观测包裹）。
 * <p>
 * 节点内的 LLM 调用经 Spring AI 自动产生的 GenAI observation 会作为本节点 observation 的子 span
 * （同线程内观测上下文经由 {@link Observation.Scope} ThreadLocal 自然继承）。并行分析师节点的虚拟线程
 * 上下文传播见 T3（#6）。
 * <p>
 * 观测层不阻断业务：observation 机器本身的 handler 异常由 Micrometer 内部吞掉；业务异常照常向上抛
 * （{@code observeChecked} 在抛出前已记录 error 并 stop）。
 */
public class TracedNodeAction implements NodeAction {

    private final String nodeName;
    private final NodeAction delegate;
    private final ObservationRegistry observationRegistry;

    public TracedNodeAction(String nodeName, NodeAction delegate, ObservationRegistry observationRegistry) {
        this.nodeName = nodeName;
        this.delegate = delegate;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // observeChecked：start -> openScope -> 调用委托 -> stop；异常时记录 error 后原样抛出。
        // 因 NodeAction.apply 抛 checked Exception，故用 CheckedCallable 变体。
        return Observation.createNotStarted(nodeName, observationRegistry)
                .observeChecked(() -> delegate.apply(state));
    }
}
