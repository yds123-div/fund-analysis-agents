package com.hex.fund.datasource.news;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 财经新闻数据采集客户端，支持东方财富资讯和新浪财经。
 */
@Slf4j
@Component
public class NewsApi {

    private static final int TIMEOUT = 10_000;
    // 东方财富财经新闻接口
    private static final String EM_NEWS_URL = "https://np-listapi.eastmoney.com/comm/web/getNewsByColumns";
    // 东方财富基金新闻
    private static final String EM_FUND_NEWS_URL = "https://searchapi.eastmoney.com/bussiness/Web/GetCMSSearchList";
    // 新浪财经 RSS
    private static final String SINA_NEWS_URL = "https://feed.mix.sina.com.cn/api/roll/get";

    /**
     * 获取财经要闻（东方财富）。
     */
    public List<Map<String, String>> fetchEastMoneyNews(int count) {
        try {
            String url = EM_NEWS_URL + "?client=web&biz=web_news_col&column=350&order=1&needInteractData=0&page_index=1&page_size=" + count;
            String resp = HttpRequest.get(url).timeout(TIMEOUT).execute().body();
            JSONObject root = JSONUtil.parseObj(resp);
            JSONObject data = root.getJSONObject("data");
            if (data == null) return List.of();
            JSONArray list = data.getJSONArray("list");
            return parseEastMoneyNewsList(list);
        } catch (Exception e) {
            log.warn("东方财富新闻获取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 搜索基金相关新闻（东方财富）。
     */
    public List<Map<String, String>> fetchFundNews(String keyword, int count) {
        try {
            JSONObject body = JSONUtil.createObj()
                    .set("KeyWord", keyword).set("PageIndex", 1).set("PageSize", count)
                    .set("SearchType", "8001").set("Columns", "");
            String resp = HttpRequest.post(EM_FUND_NEWS_URL)
                    .body(body.toString()).contentType("application/json").timeout(TIMEOUT).execute().body();
            if (resp == null || resp.trim().startsWith("<")) return List.of(); // HTML 响应，跳过
            JSONObject root = JSONUtil.parseObj(resp);
            if (!root.getBool("IsSuccess", false)) return List.of();
            JSONArray list = root.getJSONArray("Data");
            return parseFundNewsList(list);
        } catch (Exception e) {
            log.warn("东方财富基金新闻获取失败: keyword={}, {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取新浪财经新闻（备用）。
     */
    public List<Map<String, String>> fetchSinaNews(int count) {
        try {
            String url = SINA_NEWS_URL + "?pageid=153&lid=2516&num=" + count + "&versionNumber=1.2.4&ctime=" + System.currentTimeMillis() / 1000;
            String resp = HttpRequest.get(url).timeout(TIMEOUT).execute().body();
            JSONObject root = JSONUtil.parseObj(resp);
            JSONObject result = root.getJSONObject("result");
            if (result == null) return List.of();
            JSONArray list = result.getJSONArray("data");
            return parseSinaNewsList(list);
        } catch (Exception e) {
            log.warn("新浪财经新闻获取失败: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean pingEastMoney() {
        try {
            return !fetchEastMoneyNews(1).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用新闻列表解析，从 JSONArray 中按字段映射提取新闻条目。
     */
    private List<Map<String, String>> parseNewsList(JSONArray list, NewsFieldMapping mapping) {
        if (list == null) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            result.add(Map.of(
                    "title", item.getStr(mapping.titleKey, ""),
                    "url", item.getStr(mapping.urlKey, ""),
                    "source", item.getStr(mapping.sourceKey, mapping.defaultSource),
                    "time", mapping.timeExtractor.apply(item),
                    "summary", mapping.summaryExtractor.apply(item)));
        }
        return result;
    }

    private List<Map<String, String>> parseEastMoneyNewsList(JSONArray list) {
        return parseNewsList(list, new NewsFieldMapping("title", "url", "source", "东方财富",
                item -> item.getStr("showTime", item.getStr("display_time", "")),
                item -> item.getStr("digest", "")));
    }

    private List<Map<String, String>> parseFundNewsList(JSONArray list) {
        return parseNewsList(list, new NewsFieldMapping("Title", "ArticleUrl", "MediaName", "东方财富",
                item -> item.getStr("ShowTime", ""),
                item -> truncateHtml(item.getStr("Content", ""), 100)));
    }

    private List<Map<String, String>> parseSinaNewsList(JSONArray list) {
        return parseNewsList(list, new NewsFieldMapping("title", "url", "media_name", "新浪财经",
                item -> item.getStr("ctime", ""),
                item -> item.getStr("intro", "")));
    }

    private static String truncateHtml(String html, int maxLen) {
        String text = html.replaceAll("<[^>]*>", "");
        return text.substring(0, Math.min(maxLen, text.length()));
    }

    private record NewsFieldMapping(String titleKey, String urlKey, String sourceKey,
                                    String defaultSource,
                                    java.util.function.Function<JSONObject, String> timeExtractor,
                                    java.util.function.Function<JSONObject, String> summaryExtractor) {
    }
}
