package com.hex.fund.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.fund.common.enums.AnalysisPhase;
import com.hex.fund.common.progress.TaskProgressHolder;
import com.hex.fund.datasource.core.DataSourceAdapter;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.datasource.model.FundManagerData;
import com.hex.fund.datasource.model.HoldingData;
import com.hex.fund.datasource.model.NavData;
import com.hex.fund.datasource.util.NavAnalysisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据采集节点 — 从可用数据源采集基金数据。
 */
@Slf4j
@RequiredArgsConstructor
public class DataCollectionNode implements NodeAction {

    private final DataSourceManager dataSourceManager;
    private final TaskProgressHolder progressHolder;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String fundCode = (String) state.value("fundCode").orElse("");
        String batchNo = (String) state.value("batchNo").orElse("");
        progressHolder.update(batchNo, AnalysisPhase.DATA_COLLECTION.getProgress(),
                AnalysisPhase.DATA_COLLECTION.getDesc());
        log.info("[数据采集] 开始采集基金数据: 基金={}", fundCode);
        Map<String, Object> metadata = collectFundData(fundCode);
        return Map.of("metadata", metadata);
    }

    private Map<String, Object> collectFundData(String fundCode) {
        Map<String, Object> metadata = new HashMap<>();
        dataSourceManager.getFirstAvailable().ifPresent(adapter -> {
            try {
                collectBasicAndNav(adapter, fundCode, metadata);
                collectManagerAndHoldings(adapter, fundCode, metadata);
                metadata.put("dataSource", adapter.getSourceName());
            } catch (Exception e) {
                log.warn("[数据采集] 数据采集失败: {}", e.getMessage());
            }
        });
        return metadata;
    }

    private void collectBasicAndNav(DataSourceAdapter adapter,
                                    String fundCode, Map<String, Object> metadata) {
        FundBasicData basic = adapter.getFundBasic(fundCode);
        if (basic != null) metadata.put("fundBasic", basic);
        // 拉取180天净值历史，覆盖6个月窗口
        List<NavData> navHistory = adapter.getNavHistory(fundCode,
                LocalDate.now().minusDays(180), LocalDate.now());
        if (!navHistory.isEmpty()) {
            metadata.put("navHistory", navHistory);
            // 计算多时间窗口业绩摘要
            var summary = NavAnalysisUtil.analyze(navHistory);
            metadata.put("performanceSummary", summary);
        }
        // 尝试获取盘中实时估值
        try {
            var estimate = adapter.getRealTimeEstimate(fundCode);
            if (estimate != null) metadata.put("realTimeEstimate", estimate);
        } catch (Exception e) {
            log.debug("[数据采集] 实时估值获取失败（非交易时段正常）: {}", e.getMessage());
        }
    }

    private void collectManagerAndHoldings(DataSourceAdapter adapter,
                                          String fundCode, Map<String, Object> metadata) {
        FundManagerData manager = adapter.getFundManager(fundCode);
        if (manager != null) metadata.put("fundManager", manager);
        List<HoldingData> holdings = adapter.getFundHoldings(fundCode, null);
        if (!holdings.isEmpty()) metadata.put("holdings", holdings);
        log.info("[数据采集] 完成: 基金={}, 经理={}",
                fundCode, manager != null ? manager.getManagerName() : "未知");
    }
}
