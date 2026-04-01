package com.hex.fund.datasource.eastmoney;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天天基金 HTTP 客户端，提供基金估值、净值、详情、持仓、经理等数据接口。
 */
@Slf4j
@Component
public class EastMoneyApi {

    private static final String ESTIMATE_URL = "http://fundgz.1234567.com.cn/js/%s.js";
    private static final String NAV_HISTORY_URL = "https://api.fund.eastmoney.com/f10/lsjz";
    private static final String FUND_DETAIL_URL = "https://fund.eastmoney.com/pingzhongdata/%s.js";
    private static final String FUND_HOLDINGS_URL = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx";
    private static final String FUND_SEARCH_URL = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String REFERER = "https://fund.eastmoney.com/";
    private static final int TIMEOUT = 15_000;

    public String fetchEstimate(String fundCode) {
        return HttpUtil.get(String.format(ESTIMATE_URL, fundCode), TIMEOUT);
    }

    public String fetchNavHistory(String fundCode, LocalDate start, LocalDate end, int pageSize) {
        String url = NAV_HISTORY_URL + "?fundCode=" + fundCode
                + "&pageIndex=1&pageSize=" + pageSize
                + "&startDate=" + start.format(DATE_FMT)
                + "&endDate=" + end.format(DATE_FMT);
        return HttpRequest.get(url).header("Referer", REFERER).timeout(TIMEOUT).execute().body();
    }

    /**
     * 获取基金品种数据（经理、业绩、资产配置、持仓股票代码等）。
     */
    public String fetchFundDetail(String fundCode) {
        return HttpRequest.get(String.format(FUND_DETAIL_URL, fundCode))
                .header("Referer", REFERER).timeout(TIMEOUT).execute().body();
    }

    /**
     * 获取基金持仓明细（HTML 格式）。
     */
    public String fetchHoldings(String fundCode) {
        String url = FUND_HOLDINGS_URL + "?type=jjcc&code=" + fundCode + "&topline=10";
        return HttpRequest.get(url).header("Referer", "https://fundf10.eastmoney.com/")
                .timeout(TIMEOUT).execute().body();
    }

    public String searchFunds(String keyword) {
        Map<String, Object> params = new HashMap<>();
        params.put("m", 1);
        params.put("key", keyword);
        return HttpUtil.get(FUND_SEARCH_URL, params, TIMEOUT);
    }

    /**
     * 分页获取净值历史，自动合并所有页数据。
     */
    public List<String> fetchNavHistoryPaged(String fundCode, LocalDate start, LocalDate end) {
        List<String> pages = new ArrayList<>();
        int pageIndex = 1, totalCount;
        do {
            String url = NAV_HISTORY_URL + "?fundCode=" + fundCode
                    + "&pageIndex=" + pageIndex + "&pageSize=200"
                    + "&startDate=" + start.format(DATE_FMT)
                    + "&endDate=" + end.format(DATE_FMT);
            String body = HttpRequest.get(url).header("Referer", REFERER)
                    .timeout(TIMEOUT).execute().body();
            pages.add(body);
            JSONObject root = JSONUtil.parseObj(body);
            totalCount = root.getInt("TotalCount", 0);
            pageIndex++;
        } while ((pageIndex - 1) * 200 < totalCount);
        return pages;
    }

    public boolean ping() {
        try {
            String result = fetchEstimate("000001");
            return result != null && result.contains("fundcode");
        } catch (Exception e) {
            log.warn("天天基金连通性检测失败: {}", e.getMessage());
            return false;
        }
    }
}
