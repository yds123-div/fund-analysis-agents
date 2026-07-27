package com.hex.fund.datasource.eastmoney;

import cn.hutool.core.util.CharsetUtil;
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

    // 实时估值接口。天天基金 fundgz 域名已被东方财富下线(返回 HTML"页面未找到"),
    // 仅保留 https 版本作主探测,实际取数降级到新浪实时估值作为兜底。
    private static final String ESTIMATE_URL = "https://fundgz.1234567.com.cn/js/%s.js";
    private static final String SINA_ESTIMATE_URL = "https://hq.sinajs.cn/list=of%s";
    private static final String SINA_REFERER = "https://finance.sina.com.cn/";
    private static final String NAV_HISTORY_URL = "https://api.fund.eastmoney.com/f10/lsjz";
    private static final String FUND_DETAIL_URL = "https://fund.eastmoney.com/pingzhongdata/%s.js";
    private static final String FUND_HOLDINGS_URL = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx";
    private static final String FUND_SEARCH_URL = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String REFERER = "https://fund.eastmoney.com/";
    private static final int TIMEOUT = 15_000;

    /**
     * 获取实时估值。优先天天基金 fundgz(数据含盘中时间),失败则降级到新浪实时估值。
     * fundgz 域名已被东方财富下线,默认走新浪兜底。
     */
    public String fetchEstimate(String fundCode) {
        String fundgz = fetchFundgzEstimate(fundCode);
        if (fundgz != null && fundgz.contains("fundcode")) {
            return fundgz;
        }
        return fetchSinaEstimate(fundCode);
    }

    private String fetchFundgzEstimate(String fundCode) {
        try {
            return HttpRequest.get(String.format(ESTIMATE_URL, fundCode))
                    .header("Referer", REFERER).timeout(TIMEOUT).execute().body();
        } catch (Exception e) {
            log.debug("天天基金估值接口(fundgz)不可用: {}", e.getMessage());
            return null;
        }
    }

    private String fetchSinaEstimate(String fundCode) {
        try {
            byte[] bytes = HttpRequest.get(String.format(SINA_ESTIMATE_URL, fundCode))
                    .header("Referer", SINA_REFERER).timeout(TIMEOUT).execute().bodyBytes();
            return new String(bytes, CharsetUtil.charset("GBK"));
        } catch (Exception e) {
            log.warn("新浪估值接口不可用: {}", e.getMessage());
            return null;
        }
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
        // 健康检查改用净值历史接口(fundgz 估值端点下线后仍稳定可用)。
        // 原先探测 fundgz 估值端点,但东财已下线该域名(返回 HTML"页面未找到"),
        // 导致 isAvailable() 永远为 false,误判整个数据源离线。
        try {
            String body = fetchNavHistory("000001", LocalDate.now().minusDays(7), LocalDate.now(), 1);
            return body != null && body.contains("\"ErrCode\":0");
        } catch (Exception e) {
            log.warn("天天基金连通性检测失败: {}", e.getMessage());
            return false;
        }
    }
}
