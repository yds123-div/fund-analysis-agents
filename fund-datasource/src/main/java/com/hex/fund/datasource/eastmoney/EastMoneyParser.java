package com.hex.fund.datasource.eastmoney;

import cn.hutool.core.util.ReUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hex.fund.datasource.model.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses EastMoney JSONP and JSON responses into domain models.
 */
@Slf4j
public class EastMoneyParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Extract JSON from JSONP response like: jsonpgz({...});
     */
    public static String extractJsonFromJsonp(String jsonp) {
        String json = ReUtil.getGroup0("\\{.*}", jsonp);
        return json != null ? json : jsonp;
    }

    /**
     * Parse real-time estimate from fundgz API.
     */
    public static FundEstimate parseEstimate(String jsonpResponse) {
        String json = extractJsonFromJsonp(jsonpResponse);
        JSONObject obj = JSONUtil.parseObj(json);
        return FundEstimate.builder()
                .fundCode(obj.getStr("fundcode"))
                .fundName(obj.getStr("name"))
                .estimateNav(obj.getBigDecimal("gsz"))
                .estimateGrowthRate(obj.getBigDecimal("gszzl"))
                .estimateTime(parseDateTime(obj.getStr("gztime")))
                .source("eastmoney")
                .build();
    }

    /**
     * Parse NAV history from lsjz API.
     */
    public static List<NavData> parseNavHistory(String jsonResponse, String fundCode) {
        JSONObject root = JSONUtil.parseObj(jsonResponse);
        JSONObject data = root.getJSONObject("Data");
        if (data == null) return Collections.emptyList();
        JSONArray list = data.getJSONArray("LSJZList");
        if (list == null) return Collections.emptyList();

        List<NavData> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            result.add(NavData.builder()
                    .fundCode(fundCode)
                    .navDate(parseDate(item.getStr("FSRQ")))
                    .unitNav(parseBigDecimal(item.getStr("DWJZ")))
                    .accumulatedNav(parseBigDecimal(item.getStr("LJJZ")))
                    .dayGrowthRate(parseBigDecimal(item.getStr("JZZZL")))
                    .source("eastmoney")
                    .build());
        }
        return result;
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr, DATE_FMT);
    }

    private static LocalDateTime parseDateTime(String dtStr) {
        if (dtStr == null || dtStr.isBlank()) return null;
        return LocalDateTime.parse(dtStr, DATETIME_FMT);
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "--".equals(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 pingzhongdata JS 中提取变量值。
     */
    private static String extractVar(String js, String varName) {
        String pattern = "var " + varName + "\\s*=\\s*";
        int idx = js.indexOf("var " + varName);
        if (idx < 0) return null;
        int eqIdx = js.indexOf('=', idx);
        if (eqIdx < 0) return null;
        int start = eqIdx + 1;
        // 跳过空格
        while (start < js.length() && js.charAt(start) == ' ') start++;
        int end = js.indexOf(';', start);
        if (end < 0) end = js.length();
        return js.substring(start, end).trim();
    }

    /**
     * 从 pingzhongdata 解析基金基本信息（名称、经理、费率、收益率等）。
     */
    public static FundBasicData parseFundDetail(String js, String fundCode) {
        if (js == null || js.isBlank()) return null;
        String name = extractStringVar(js, "fS_name");
        String sourceRate = extractStringVar(js, "fund_sourceRate");
        String rate = extractStringVar(js, "fund_Rate");
        String syl1n = extractStringVar(js, "syl_1n");
        String syl6y = extractStringVar(js, "syl_6y");
        String syl3y = extractStringVar(js, "syl_3y");
        String syl1y = extractStringVar(js, "syl_1y");
        // 解析基金经理
        String managerName = null, workTime = null, fundSize = null;
        String managerJson = extractVar(js, "Data_currentFundManager");
        if (managerJson != null && managerJson.startsWith("[")) {
            try {
                JSONArray arr = JSONUtil.parseArray(managerJson);
                if (!arr.isEmpty()) {
                    JSONObject mgr = arr.getJSONObject(0);
                    managerName = mgr.getStr("name");
                    workTime = mgr.getStr("workTime");
                    fundSize = mgr.getStr("fundSize");
                }
            } catch (Exception e) {
                log.debug("解析基金经理失败: {}", e.getMessage());
            }
        }
        return FundBasicData.builder()
                .fundCode(fundCode).fundName(name).fundManager(managerName)
                .source("eastmoney").build();
    }

    /**
     * 从 pingzhongdata 解析基金经理详细信息。
     */
    public static FundManagerData parseFundManager(String js, String fundCode) {
        String managerJson = extractVar(js, "Data_currentFundManager");
        if (managerJson == null || !managerJson.startsWith("[")) return null;
        try {
            JSONArray arr = JSONUtil.parseArray(managerJson);
            if (arr.isEmpty()) return null;
            JSONObject mgr = arr.getJSONObject(0);
            String workTime = mgr.getStr("workTime");
            int years = 0;
            if (workTime != null && workTime.contains("年")) {
                try {
                    years = Integer.parseInt(workTime.substring(0, workTime.indexOf("年")));
                } catch (Exception ignored) {
                }
            }
            return FundManagerData.builder()
                    .fundCode(fundCode).managerName(mgr.getStr("name"))
                    .managerId(mgr.getStr("id"))
                    .yearsOfExperience(years)
                    .totalScale(parseFundSize(mgr.getStr("fundSize")))
                    .source("eastmoney").build();
        } catch (Exception e) {
            log.warn("解析基金经理失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从持仓 HTML 解析前十大持仓。
     */
    public static List<HoldingData> parseHoldings(String html, String fundCode) {
        if (html == null || html.isBlank()) return Collections.emptyList();
        List<HoldingData> holdings = new ArrayList<>();
        // 解析 apidata 中的 HTML 表格
        String content = html;
        if (content.contains("content:\"")) {
            int start = content.indexOf("content:\"") + 9;
            int end = content.lastIndexOf("\"");
            if (end > start) content = content.substring(start, end).replace("\\\"", "\"");
        }
        // 提取 <tbody> 中的行
        String[] rows = content.split("<tr>");
        for (String row : rows) {
            if (!row.contains("<td>") || row.contains("<th")) continue;
            String[] cells = row.split("</td>");
            if (cells.length < 8) continue;
            try {
                String stockCode = extractText(cells[1]);
                String stockName = extractText(cells[2]);
                String percent = extractText(cells[6]).replace("%", "");
                holdings.add(HoldingData.builder()
                        .fundCode(fundCode).stockCode(stockCode).stockName(stockName)
                        .holdingPercent(parseBigDecimal(percent))
                        .reportDate(extractReportDate(html))
                        .source("eastmoney").build());
            } catch (Exception e) {
                log.debug("解析持仓行失败: {}", e.getMessage());
            }
        }
        return holdings;
    }

    private static String extractText(String html) {
        return html.replaceAll("<[^>]*>", "").trim();
    }

    private static String extractStringVar(String js, String varName) {
        String val = extractVar(js, varName);
        if (val == null) return null;
        return val.replace("\"", "").replace("'", "").trim();
    }

    private static BigDecimal parseFundSize(String sizeStr) {
        if (sizeStr == null) return null;
        String num = sizeStr.replaceAll("[^0-9.]", "");
        return parseBigDecimal(num);
    }

    private static String extractReportDate(String html) {
        // 从 "截止至：2025-12-31" 提取日期
        int idx = html.indexOf("截止至：");
        if (idx < 0) idx = html.indexOf("截止至:");
        if (idx < 0) return null;
        String sub = html.substring(idx + 4, Math.min(idx + 20, html.length()));
        return ReUtil.getGroup0("\\d{4}-\\d{2}-\\d{2}", sub);
    }
}
