package com.hex.fund.admin.controller;

import com.hex.fund.common.model.ApiResponse;
import com.hex.fund.common.security.SecurityContext;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.util.FundUtil;
import com.hex.fund.service.entity.WatchList;
import com.hex.fund.service.watchlist.WatchListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自选基金管理 API。
 */
@Tag(name = "WatchList", description = "自选基金管理")
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchListController {

    private final WatchListService watchListService;
    private final DataSourceManager dataSourceManager;

    @Operation(summary = "查询自选列表")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<WatchList> items = watchListService.listByUser(SecurityContext.getCurrentUserId());
        List<Map<String, Object>> result = items.stream().map(w -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", w.getId());
            map.put("fundCode", w.getFundCode());
            map.put("fundName", FundUtil.resolveFundName(dataSourceManager, w.getFundCode()));
            map.put("notes", w.getNotes());
            map.put("priority", w.getPriority());
            map.put("createdAt", w.getCreatedAt());
            return map;
        }).toList();
        return ApiResponse.ok(result);
    }

    @Operation(summary = "添加自选基金")
    @PostMapping("/{fundCode}")
    public ApiResponse<Void> add(@PathVariable String fundCode, @RequestParam(defaultValue = "") String notes) {
        watchListService.add(SecurityContext.getCurrentUserId(), fundCode, notes);
        return ApiResponse.ok();
    }

    @Operation(summary = "移除自选基金")
    @DeleteMapping("/{fundCode}")
    public ApiResponse<Void> remove(@PathVariable String fundCode) {
        watchListService.remove(SecurityContext.getCurrentUserId(), fundCode);
        return ApiResponse.ok();
    }
}
