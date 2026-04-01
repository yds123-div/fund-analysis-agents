package com.hex.fund.datasource.core;

import com.hex.fund.datasource.model.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Unified data source adapter interface.
 * All data sources (EastMoney, Tushare, etc.) implement this.
 */
public interface DataSourceAdapter {

    String getSourceName();

    String getSourceCode();

    boolean isAvailable();

    // Fund basic info
    FundBasicData getFundBasic(String fundCode);

    List<FundBasicData> searchFunds(String keyword);

    // NAV data
    List<NavData> getNavHistory(String fundCode, LocalDate start, LocalDate end);

    NavData getLatestNav(String fundCode);

    FundEstimate getRealTimeEstimate(String fundCode);

    // Holdings (from quarterly reports)
    List<HoldingData> getFundHoldings(String fundCode, String reportDate);

    // Manager info
    FundManagerData getFundManager(String fundCode);
}
