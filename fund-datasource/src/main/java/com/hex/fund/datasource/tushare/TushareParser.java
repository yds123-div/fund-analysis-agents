package com.hex.fund.datasource.tushare;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.datasource.model.NavData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tushare 响应解析器，将 fields + items 格式转换为领域模型。
 */
@Slf4j
public class TushareParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 解析基金净值列表。
     */
    public static List<NavData> parseNavList(String json, String fundCode) {
        JSONObject data = extractData(json);
        if (data == null) return Collections.emptyList();
        JSONArray fields = data.getJSONArray("fields");
        JSONArray items = data.getJSONArray("items");
        if (fields == null || items == null) return Collections.emptyList();
        List<String> fieldList = fields.toList(String.class);
        int navDateIdx = fieldList.indexOf("nav_date");
        int unitNavIdx = fieldList.indexOf("unit_nav");
        int accumNavIdx = fieldList.indexOf("accum_nav");
        List<NavData> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            JSONArray row = items.getJSONArray(i);
            result.add(NavData.builder().fundCode(fundCode)
                    .navDate(parseDate(getStr(row, navDateIdx)))
                    .unitNav(getBd(row, unitNavIdx))
                    .accumulatedNav(getBd(row, accumNavIdx))
                    .source("tushare").build());
        }
        return result;
    }

    /**
     * 解析基金基本信息。
     */
    public static FundBasicData parseFundBasic(String json, String fundCode) {
        JSONObject data = extractData(json);
        if (data == null) return null;
        JSONArray fields = data.getJSONArray("fields");
        JSONArray items = data.getJSONArray("items");
        if (fields == null || items == null || items.isEmpty()) return null;
        List<String> fieldList = fields.toList(String.class);
        JSONArray row = items.getJSONArray(0);
        return FundBasicData.builder().fundCode(fundCode)
                .fundName(getStr(row, fieldList.indexOf("name")))
                .managementCompany(getStr(row, fieldList.indexOf("management")))
                .fundType(getStr(row, fieldList.indexOf("fund_type")))
                .source("tushare").build();
    }

    /**
     * 基金代码格式转换：000001 -> 000001.OF
     */
    public static String toTushareCode(String fundCode) {
        if (fundCode == null) return null;
        return fundCode.contains(".") ? fundCode : fundCode + ".OF";
    }

    private static JSONObject extractData(String json) {
        if (json == null || json.isBlank()) return null;
        JSONObject root = JSONUtil.parseObj(json);
        if (root.getInt("code", -1) != 0) {
            log.warn("Tushare 返回错误: {}", root.getStr("msg"));
            return null;
        }
        return root.getJSONObject("data");
    }

    private static String getStr(JSONArray row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        Object val = row.get(idx);
        return val == null ? null : val.toString();
    }

    private static BigDecimal getBd(JSONArray row, int idx) {
        String str = getStr(row, idx);
        if (str == null || str.isBlank()) return null;
        try {
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
