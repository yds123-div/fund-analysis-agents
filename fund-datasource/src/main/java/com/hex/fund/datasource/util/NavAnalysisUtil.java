package com.hex.fund.datasource.util;

import com.hex.fund.datasource.model.NavData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 净值分析工具类 — 基于历史净值计算多时间窗口收益率、回撤、波动率等。
 */
@Slf4j
public final class NavAnalysisUtil {

    private NavAnalysisUtil() {
    }

    /** 时间窗口定义：名称 -> 天数 */
    private static final Map<String, Integer> WINDOWS = new LinkedHashMap<>() {{
        put("1日", 1);
        put("3日", 3);
        put("1周", 7);
        put("2周", 14);
        put("1月", 30);
        put("3月", 90);
        put("6月", 180);
    }};

    /**
     * 从净值历史计算多维度业绩摘要。
     * navHistory 可以是任意排序，内部会按日期降序排列。
     */
    public static NavPerformanceSummary analyze(List<NavData> navHistory) {
        if (navHistory == null || navHistory.isEmpty()) {
            return NavPerformanceSummary.empty();
        }
        List<NavData> sorted = navHistory.stream()
                .filter(n -> n.getNavDate() != null && n.getUnitNav() != null)
                .sorted(Comparator.comparing(NavData::getNavDate).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return NavPerformanceSummary.empty();
        }
        NavData latest = sorted.getFirst();
        BigDecimal latestNav = latest.getUnitNav();
        LocalDate latestDate = latest.getNavDate();

        // 多窗口收益率
        Map<String, BigDecimal> returnRates = new LinkedHashMap<>();
        WINDOWS.forEach((name, days) -> {
            BigDecimal rate = calcReturnRate(sorted, latestNav, latestDate, days);
            returnRates.put(name, rate);
        });

        // 近5个交易日净值走势
        String navTrend = sorted.stream().limit(5)
                .map(n -> n.getNavDate() + ":" + n.getUnitNav())
                .collect(Collectors.joining(" → "));

        // 回撤与波动率
        BigDecimal maxDrawdown30 = calcMaxDrawdown(sorted, 30);
        BigDecimal maxDrawdown90 = calcMaxDrawdown(sorted, 90);
        BigDecimal volatility30 = calcAnnualizedVolatility(sorted, 30);

        return new NavPerformanceSummary(latestNav, latestDate, returnRates,
                maxDrawdown30, maxDrawdown90, volatility30, navTrend);
    }

    /** 计算距今 N 天窗口的收益率 */
    private static BigDecimal calcReturnRate(List<NavData> sorted,
                                             BigDecimal latestNav, LocalDate latestDate, int days) {
        LocalDate targetDate = latestDate.minusDays(days);
        // 找到目标日期当天或之前最近的一条记录
        NavData baseline = sorted.stream()
                .filter(n -> !n.getNavDate().isAfter(targetDate))
                .findFirst().orElse(null);
        if (baseline == null || baseline.getUnitNav() == null
                || baseline.getUnitNav().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latestNav.subtract(baseline.getUnitNav())
                .divide(baseline.getUnitNav(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 计算近 N 天最大回撤（百分比） */
    private static BigDecimal calcMaxDrawdown(List<NavData> sorted, int days) {
        List<NavData> window = sorted.stream()
                .limit(days).sorted(Comparator.comparing(NavData::getNavDate)).toList();
        if (window.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal peak = window.getFirst().getUnitNav();
        BigDecimal maxDd = BigDecimal.ZERO;
        for (NavData n : window) {
            if (n.getUnitNav().compareTo(peak) > 0) {
                peak = n.getUnitNav();
            }
            BigDecimal dd = peak.subtract(n.getUnitNav())
                    .divide(peak, 6, RoundingMode.HALF_UP);
            if (dd.compareTo(maxDd) > 0) {
                maxDd = dd;
            }
        }
        return maxDd.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 计算近 N 天年化波动率 */
    private static BigDecimal calcAnnualizedVolatility(List<NavData> sorted, int days) {
        List<NavData> window = sorted.stream()
                .limit(days).sorted(Comparator.comparing(NavData::getNavDate)).toList();
        if (window.size() < 3) {
            return BigDecimal.ZERO;
        }
        double[] dailyReturns = new double[window.size() - 1];
        for (int i = 1; i < window.size(); i++) {
            double prev = window.get(i - 1).getUnitNav().doubleValue();
            double curr = window.get(i).getUnitNav().doubleValue();
            dailyReturns[i - 1] = prev > 0 ? (curr - prev) / prev : 0;
        }
        double mean = 0;
        for (double r : dailyReturns) mean += r;
        mean /= dailyReturns.length;
        double variance = 0;
        for (double r : dailyReturns) variance += (r - mean) * (r - mean);
        variance /= dailyReturns.length;
        double annualized = Math.sqrt(variance) * Math.sqrt(242) * 100;
        return BigDecimal.valueOf(annualized).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 净值业绩摘要，toString() 输出人类可读文本供 LLM 分析。
     */
    public record NavPerformanceSummary(
            BigDecimal latestNav, LocalDate latestDate,
            Map<String, BigDecimal> returnRates,
            BigDecimal maxDrawdown30d, BigDecimal maxDrawdown90d,
            BigDecimal volatility30d, String navTrend
    ) {

        public static NavPerformanceSummary empty() {
            return new NavPerformanceSummary(null, null, Map.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "暂无数据");
        }

        @Override
        public String toString() {
            if (latestNav == null) {
                return "暂无净值数据";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【最新净值】%s（%s）\n", latestNav, latestDate));
            sb.append("【多窗口收益率】");
            returnRates.forEach((name, rate) ->
                    sb.append(String.format("%s:%s%% ", name,
                            rate != null ? rate.toPlainString() : "N/A")));
            sb.append(String.format("\n【风险指标】近30日最大回撤:%.2f%% " +
                            "近90日最大回撤:%.2f%% 近30日年化波动率:%.2f%%",
                    maxDrawdown30d, maxDrawdown90d, volatility30d));
            sb.append("\n【近期走势】").append(navTrend);
            return sb.toString();
        }
    }
}
