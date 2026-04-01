package com.hex.fund.datasource.tushare;

import com.hex.fund.datasource.core.DataSourceAdapter;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Tushare 数据源适配器，作为备用数据源提供基金净值和基本信息。
 * 免费版不支持实时估值、持仓、经理等数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TushareAdapter implements DataSourceAdapter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final TushareApi api;
    private final DataSourceManager dataSourceManager;

    @PostConstruct
    public void init() {
        if (!api.hasToken()) {
            log.info("Tushare token 未配置, 跳过注册");
            return;
        }
        dataSourceManager.register(this, 20);
    }

    @Override
    public String getSourceName() {
        return "Tushare";
    }

    @Override
    public String getSourceCode() {
        return "tushare";
    }

    @Override
    public boolean isAvailable() {
        return api.hasToken() && api.ping();
    }

    @Override
    public FundBasicData getFundBasic(String fundCode) {
        String tsCode = TushareParser.toTushareCode(fundCode);
        String response = api.fetchFundBasic(tsCode);
        return TushareParser.parseFundBasic(response, fundCode);
    }

    @Override
    public List<FundBasicData> searchFunds(String keyword) {
        return Collections.emptyList(); // Tushare 免费版不支持模糊搜索
    }

    @Override
    public List<NavData> getNavHistory(String fundCode, LocalDate start, LocalDate end) {
        String tsCode = TushareParser.toTushareCode(fundCode);
        String response = api.fetchFundNav(tsCode, start.format(FMT), end.format(FMT));
        return TushareParser.parseNavList(response, fundCode);
    }

    @Override
    public NavData getLatestNav(String fundCode) {
        LocalDate end = LocalDate.now();
        List<NavData> history = getNavHistory(fundCode, end.minusDays(7), end);
        return history.isEmpty() ? null : history.getFirst();
    }

    @Override
    public FundEstimate getRealTimeEstimate(String fundCode) {
        return null; // Tushare 免费版不支持实时估值
    }

    @Override
    public List<HoldingData> getFundHoldings(String fundCode, String reportDate) {
        return Collections.emptyList(); // Tushare 免费版不支持持仓数据
    }

    @Override
    public FundManagerData getFundManager(String fundCode) {
        return null; // Tushare 免费版不支持经理数据
    }
}
