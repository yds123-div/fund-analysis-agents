package com.hex.fund.datasource.eastmoney;

import com.hex.fund.datasource.core.DataSourceAdapter;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 天天基金数据源适配器，主数据源。
 * 提供基金基本信息、净值、估值、持仓、经理等完整数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyAdapter implements DataSourceAdapter {

    private final EastMoneyApi api;
    private final DataSourceManager dataSourceManager;

    @PostConstruct
    public void init() {
        dataSourceManager.register(this, 10);
    }

    @Override
    public String getSourceName() {
        return "天天基金";
    }

    @Override
    public String getSourceCode() {
        return "eastmoney";
    }

    @Override
    public boolean isAvailable() {
        return api.ping();
    }

    @Override
    @Cacheable(value = "fundBasic", key = "#fundCode", unless = "#result == null")
    public FundBasicData getFundBasic(String fundCode) {
        // 从 pingzhongdata 获取丰富的基金信息
        try {
            String detail = api.fetchFundDetail(fundCode);
            FundBasicData basic = EastMoneyParser.parseFundDetail(detail, fundCode);
            if (basic != null) {
                // 补充净值数据
                NavData nav = getLatestNav(fundCode);
                if (nav != null) {
                    basic.setNav(nav.getUnitNav());
                    basic.setAccumulatedNav(nav.getAccumulatedNav());
                    basic.setDayGrowthRate(nav.getDayGrowthRate());
                }
                return basic;
            }
        } catch (Exception e) {
            log.warn("从品种数据获取基金信息失败: {}, 降级到净值接口", e.getMessage());
        }
        // 降级：仅从净值接口获取
        NavData nav = getLatestNav(fundCode);
        if (nav == null) return null;
        return FundBasicData.builder().fundCode(fundCode)
                .nav(nav.getUnitNav()).accumulatedNav(nav.getAccumulatedNav())
                .dayGrowthRate(nav.getDayGrowthRate()).source("eastmoney").build();
    }

    @Override
    public List<FundBasicData> searchFunds(String keyword) {
        try {
            String response = api.searchFunds(keyword);
            log.debug("基金搜索响应长度: {}", response != null ? response.length() : 0);
            // TODO: 解析搜索结果
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("基金搜索失败: 关键词={}", keyword);
            return Collections.emptyList();
        }
    }

    @Override
    public List<NavData> getNavHistory(String fundCode, LocalDate start, LocalDate end) {
        try {
            int days = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
            if (days <= 200) {
                String response = api.fetchNavHistory(fundCode, start, end, days);
                return EastMoneyParser.parseNavHistory(response, fundCode);
            }
            // 超过200条自动分页获取并合并
            List<NavData> all = new ArrayList<>();
            for (String page : api.fetchNavHistoryPaged(fundCode, start, end)) {
                all.addAll(EastMoneyParser.parseNavHistory(page, fundCode));
            }
            return all;
        } catch (Exception e) {
            log.error("获取净值历史失败: 基金={}", fundCode);
            return Collections.emptyList();
        }
    }

    @Override
    public NavData getLatestNav(String fundCode) {
        List<NavData> history = getNavHistory(fundCode, LocalDate.now().minusDays(7), LocalDate.now());
        return history.isEmpty() ? null : history.getFirst();
    }

    @Override
    @Cacheable(value = "fundEstimate", key = "#fundCode", unless = "#result == null")
    public FundEstimate getRealTimeEstimate(String fundCode) {
        try {
            return EastMoneyParser.parseEstimate(api.fetchEstimate(fundCode));
        } catch (Exception e) {
            log.error("获取实时估值失败: 基金={}", fundCode);
            return null;
        }
    }

    @Override
    @Cacheable(value = "fundHoldings", key = "#fundCode", unless = "#result.isEmpty()")
    public List<HoldingData> getFundHoldings(String fundCode, String reportDate) {
        try {
            return EastMoneyParser.parseHoldings(api.fetchHoldings(fundCode), fundCode);
        } catch (Exception e) {
            log.error("获取持仓数据失败: 基金={}", fundCode);
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "fundManager", key = "#fundCode", unless = "#result == null")
    public FundManagerData getFundManager(String fundCode) {
        try {
            return EastMoneyParser.parseFundManager(api.fetchFundDetail(fundCode), fundCode);
        } catch (Exception e) {
            log.error("获取基金经理信息失败: 基金={}", fundCode);
            return null;
        }
    }
}
