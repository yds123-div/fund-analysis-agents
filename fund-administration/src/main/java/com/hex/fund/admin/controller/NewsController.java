package com.hex.fund.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.news.NewsService;
import com.hex.fund.datasource.util.FundUtil;
import com.hex.fund.service.entity.WatchList;
import com.hex.fund.service.mapper.WatchListMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基金动态 API，提供市场新闻和自选基金新闻。
 */
@Tag(name = "News", description = "基金动态与新闻")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final WatchListMapper watchListMapper;
    private final DataSourceManager dataSourceManager;

    @Operation(summary = "获取市场要闻")
    @GetMapping("/market")
    public ApiResponse<List<Map<String, String>>> marketNews(
            @RequestParam(defaultValue = "20") int count) {
        return ApiResponse.ok(newsService.getMarketNews(count));
    }

    @Operation(summary = "搜索基金相关新闻")
    @GetMapping("/fund")
    public ApiResponse<List<Map<String, String>>> fundNews(
            @RequestParam String keyword, @RequestParam(defaultValue = "10") int count) {
        return ApiResponse.ok(newsService.getFundNews(keyword, count));
    }

    @Operation(summary = "获取自选基金新闻聚合")
    @GetMapping("/watchlist")
    public ApiResponse<List<Map<String, Object>>> watchlistNews() {
        Long userId = SecurityContext.getCurrentUserId();
        List<WatchList> watchList = watchListMapper.selectList(
                new LambdaQueryWrapper<WatchList>().eq(WatchList::getUserId, userId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WatchList w : watchList) {
            // 用基金名称搜索新闻，比基金代码更能匹配到相关内容
            String fundName = FundUtil.resolveFundName(dataSourceManager, w.getFundCode());
            String keyword = fundName.isEmpty() ? w.getFundCode() : fundName;
            List<Map<String, String>> news = newsService.getFundNews(keyword, 5);
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("fundCode", w.getFundCode());
            group.put("fundName", fundName);
            group.put("news", news);
            if (!news.isEmpty()) result.add(group);
        }
        return ApiResponse.ok(result);
    }
}
