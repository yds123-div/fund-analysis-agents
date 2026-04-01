package com.hex.fund.service.portfolio;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.datasource.core.DataSourceManager;
import com.hex.fund.datasource.model.FundBasicData;
import com.hex.fund.datasource.model.NavData;
import com.hex.fund.datasource.util.FundUtil;
import com.hex.fund.service.entity.FundPortfolio;
import com.hex.fund.service.mapper.FundPortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 持仓管理、收益计算与组合分析服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final Map<String, List<String>> STYLE_KEYWORDS = Map.of(
            "growth", List.of("成长", "创新", "科技"),
            "value", List.of("价值", "红利", "蓝筹"));
    private static final Map<String, List<String>> CAP_KEYWORDS = Map.of(
            "largeCap", List.of("大盘", "蓝筹", "沪深300"),
            "midCap", List.of("中小", "中证500"),
            "smallCap", List.of("小盘", "创业板"));
    private final FundPortfolioMapper portfolioMapper;
    private final DataSourceManager dataSourceManager;

    public List<FundPortfolio> listByUser(Long userId) {
        return portfolioMapper.selectList(new LambdaQueryWrapper<FundPortfolio>()
                .eq(FundPortfolio::getUserId, userId));
    }

    public void addOrUpdate(FundPortfolio portfolio) {
        FundPortfolio existing = portfolioMapper.selectOne(new LambdaQueryWrapper<FundPortfolio>()
                .eq(FundPortfolio::getUserId, portfolio.getUserId())
                .eq(FundPortfolio::getFundCode, portfolio.getFundCode()));
        if (existing != null) {
            existing.setHoldingAmount(portfolio.getHoldingAmount());
            existing.setAvgCost(portfolio.getAvgCost());
            existing.setNotes(portfolio.getNotes());
            portfolioMapper.updateById(existing);
        } else {
            portfolioMapper.insert(portfolio);
        }
    }

    public void remove(Long userId, String fundCode) {
        portfolioMapper.delete(new LambdaQueryWrapper<FundPortfolio>()
                .eq(FundPortfolio::getUserId, userId).eq(FundPortfolio::getFundCode, fundCode));
    }

    /** 计算单只基金持仓收益 */
    public PortfolioPnL calculatePnL(FundPortfolio portfolio) {
        BigDecimal currentNav = getCurrentNav(portfolio.getFundCode());
        if (currentNav == null || portfolio.getAvgCost() == null || portfolio.getHoldingAmount() == null)
            return new PortfolioPnL(portfolio.getFundCode(), null, null, null, null);
        BigDecimal marketValue = currentNav.multiply(portfolio.getHoldingAmount());
        BigDecimal costValue = portfolio.getAvgCost().multiply(portfolio.getHoldingAmount());
        BigDecimal pnl = marketValue.subtract(costValue);
        BigDecimal pnlRate = costValue.compareTo(BigDecimal.ZERO) > 0
                ? pnl.divide(costValue, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new PortfolioPnL(portfolio.getFundCode(), currentNav, marketValue, pnl, pnlRate);
    }

    /** 持仓组合分析：分布、风格、持仓集中度 */
    public Map<String, Object> analyzePortfolio(Long userId) {
        List<FundPortfolio> portfolios = listByUser(userId);
        List<Map<String, Object>> distribution = buildDistribution(portfolios);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", distribution);
        result.put("styleAnalysis", buildStyleAnalysis(portfolios));
        result.put("concentration", calcConcentration(distribution));
        return result;
    }

    /** 组合收益走势（基于各基金净值历史加权计算） */
    public List<Map<String, Object>> getPortfolioTrend(Long userId, int days) {
        List<FundPortfolio> portfolios = listByUser(userId);
        if (portfolios.isEmpty()) return List.of();
        LocalDate end = LocalDate.now(), start = end.minusDays(days);
        Map<String, List<NavData>> navMap = new HashMap<>();
        for (FundPortfolio p : portfolios) {
            dataSourceManager.getFirstAvailable().ifPresent(adapter ->
                    navMap.put(p.getFundCode(), adapter.getNavHistory(p.getFundCode(), start, end)));
        }
        return calcPortfolioTrend(portfolios, navMap);
    }

    public BigDecimal getCurrentNav(String fundCode, boolean includeTodayReturn) {
        return getCurrentNav(fundCode);
    }

    // ---- 组合方法 ----

    private List<Map<String, Object>> buildDistribution(List<FundPortfolio> portfolios) {
        List<PortfolioPnL> pnls = portfolios.stream().map(this::calculatePnL).toList();
        BigDecimal total = pnls.stream().map(PortfolioPnL::marketValue)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) return List.of();
        return pnls.stream().filter(p -> p.marketValue() != null).map(p -> {
            BigDecimal pct = p.marketValue().divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            String fundName = FundUtil.resolveFundName(dataSourceManager, p.fundCode());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fundCode", p.fundCode());
            map.put("fundName", fundName);
            map.put("marketValue", p.marketValue());
            map.put("percent", pct);
            return map;
        }).toList();
    }

    /** 风格分析：通过基金名称关键词匹配风格和市值分布 */
    private Map<String, Object> buildStyleAnalysis(List<FundPortfolio> portfolios) {
        int total = portfolios.size();
        if (total == 0)
            return Map.of("growth", 0, "value", 0, "balanced", 0,
                    "largeCap", 0, "midCap", 0, "smallCap", 0);
        Map<String, Integer> styleCounts = new HashMap<>(Map.of(
                "growth", 0, "value", 0, "balanced", 0));
        Map<String, Integer> capCounts = new HashMap<>(Map.of(
                "largeCap", 0, "midCap", 0, "smallCap", 0));
        for (FundPortfolio p : portfolios) {
            String name = resolveFundName(p.getFundCode());
            String style = matchKeyword(name, STYLE_KEYWORDS, "balanced");
            styleCounts.merge(style, 1, Integer::sum);
            String cap = matchKeyword(name, CAP_KEYWORDS, "largeCap");
            capCounts.merge(cap, 1, Integer::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        styleCounts.forEach((k, v) -> result.put(k, v * 100 / total));
        capCounts.forEach((k, v) -> result.put(k, v * 100 / total));
        return result;
    }

    private String matchKeyword(String name, Map<String, List<String>> keywordMap, String defaultKey) {
        for (var entry : keywordMap.entrySet()) {
            if (entry.getValue().stream().anyMatch(name::contains)) return entry.getKey();
        }
        return defaultKey;
    }

    private String resolveFundName(String fundCode) {
        FundBasicData basic = dataSourceManager.getAggregatedFundBasic(fundCode);
        return basic != null && basic.getFundName() != null ? basic.getFundName() : "";
    }

    private BigDecimal calcConcentration(List<Map<String, Object>> distribution) {
        if (distribution.isEmpty()) return BigDecimal.ZERO;
        return (BigDecimal) distribution.stream()
                .max(Comparator.comparing(m -> (BigDecimal) m.get("percent")))
                .map(m -> ((BigDecimal) m.get("percent"))
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
    }

    /** 计算组合收益走势 */
    private List<Map<String, Object>> calcPortfolioTrend(List<FundPortfolio> portfolios,
                                                         Map<String, List<NavData>> navMap) {
        Set<LocalDate> allDates = navMap.values().stream().flatMap(List::stream)
                .map(NavData::getNavDate).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        List<Map<String, Object>> trend = new ArrayList<>();
        BigDecimal baseValue = null;
        for (LocalDate date : allDates) {
            var dayResult = calcDayValue(portfolios, navMap, date);
            BigDecimal returnRate;
            if (dayResult.cost.compareTo(BigDecimal.ZERO) > 0) {
                returnRate = dayResult.value.subtract(dayResult.cost)
                        .divide(dayResult.cost, 4, RoundingMode.HALF_UP);
            } else {
                if (baseValue == null && dayResult.value.compareTo(BigDecimal.ZERO) > 0)
                    baseValue = dayResult.value;
                returnRate = baseValue != null && baseValue.compareTo(BigDecimal.ZERO) > 0
                        ? dayResult.value.subtract(baseValue)
                        .divide(baseValue, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            }
            trend.add(Map.of("date", date.toString(),
                    "totalValue", dayResult.value, "returnRate", returnRate));
        }
        return trend;
    }

    private DayValueResult calcDayValue(List<FundPortfolio> portfolios,
                                        Map<String, List<NavData>> navMap, LocalDate date) {
        BigDecimal dayValue = BigDecimal.ZERO, dayCost = BigDecimal.ZERO;
        for (FundPortfolio p : portfolios) {
            List<NavData> navs = navMap.getOrDefault(p.getFundCode(), List.of());
            NavData dayNav = navs.stream()
                    .filter(n -> date.equals(n.getNavDate())).findFirst().orElse(null);
            if (dayNav != null && dayNav.getUnitNav() != null && p.getHoldingAmount() != null) {
                dayValue = dayValue.add(dayNav.getUnitNav().multiply(p.getHoldingAmount()));
                if (p.getAvgCost() != null)
                    dayCost = dayCost.add(p.getAvgCost().multiply(p.getHoldingAmount()));
            }
        }
        return new DayValueResult(dayValue, dayCost);
    }

    private BigDecimal getCurrentNav(String fundCode) {
        return dataSourceManager.getFirstAvailable().map(adapter -> {
            NavData nav = adapter.getLatestNav(fundCode);
            return nav != null ? nav.getUnitNav() : null;
        }).orElse(null);
    }

    public record PortfolioPnL(String fundCode, BigDecimal currentNav,
                               BigDecimal marketValue, BigDecimal pnl, BigDecimal pnlRate) {
    }

    private record DayValueResult(BigDecimal value, BigDecimal cost) {
    }
}
