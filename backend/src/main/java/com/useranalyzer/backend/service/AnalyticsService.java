package com.useranalyzer.backend.service;

import com.useranalyzer.backend.dto.*;
import com.useranalyzer.backend.entity.*;
import com.useranalyzer.backend.repository.*;
import com.useranalyzer.backend.dto.AssociationRuleData.RuleItem;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SessionAggrStatRepository sessionAggrStatRepository;
    private final Top10CategoryRepository top10CategoryRepository;
    private final PageConvertRateRepository pageConvertRateRepository;
    private final UserLevelStatRepository userLevelStatRepository;
    private final ProductAssociationRepository productAssociationRepository;

    @Cacheable(value = "sessionStats", key = "#taskId + '_length'", unless = "#result == null")
    public LineChartData getSessionLengthDistribution(Long taskId) {
        SessionAggrStat stat = sessionAggrStatRepository.findByTaskId(taskId);
        if (stat == null) {
            return null;
        }

        return new LineChartData(
            "会话时长分布",
            Arrays.asList("1-3秒", "4-6秒", "7-9秒", "10-30秒", "30-60秒", "1-3分钟", "3-10分钟", "10-30分钟", ">30分钟"),
            Arrays.asList(
                stat.getVisitLength1s3s().intValue(),
                stat.getVisitLength4s6s().intValue(),
                stat.getVisitLength7s9s().intValue(),
                stat.getVisitLength10s30s().intValue(),
                stat.getVisitLength30s60s().intValue(),
                stat.getVisitLength1m3m().intValue(),
                stat.getVisitLength3m10m().intValue(),
                stat.getVisitLength10m30m().intValue(),
                stat.getVisitLength30m().intValue()
            )
        );
    }

    @Cacheable(value = "sessionStats", key = "#taskId + '_step'", unless = "#result == null")
    public BarChartData getStepLengthDistribution(Long taskId) {
        SessionAggrStat stat = sessionAggrStatRepository.findByTaskId(taskId);
        if (stat == null) {
            return null;
        }

        return new BarChartData(
            Arrays.asList("1-3", "4-6", "7-9", "10-30", "30-60", ">60"),
            Arrays.asList(new BarChartData.BarSeries("Step Count", Arrays.asList(
                stat.getStepLength1_3(),
                stat.getStepLength4_6(),
                stat.getStepLength7_9(),
                stat.getStepLength10_30(),
                stat.getStepLength30_60(),
                stat.getStepLength60()
            )))
        );
    }

    @Cacheable(value = "top10Categories", key = "#taskId", unless = "#result == null or #result.getSeries().isEmpty()")
    public BarChartData getTop10Categories(Long taskId) {
        List<Top10Category> categories = top10CategoryRepository.findByTaskIdOrderByClickCountDesc(taskId);
        
        if (categories == null || categories.isEmpty()) {
            return new BarChartData(Collections.emptyList(), Collections.emptyList());
        }

        List<String> categoryIds = categories.stream()
                .map(Top10Category::getCategoryId)
                .map(String::valueOf)
                .collect(Collectors.toList());

        List<Long> clickCounts = categories.stream()
                .map(Top10Category::getClickCount)
                .collect(Collectors.toList());

        List<Long> orderCounts = categories.stream()
                .map(Top10Category::getOrderCount)
                .collect(Collectors.toList());

        List<Long> payCounts = categories.stream()
                .map(Top10Category::getPayCount)
                .collect(Collectors.toList());

        return new BarChartData(
            categoryIds,
            Arrays.asList(
                new BarChartData.BarSeries("Clicks", clickCounts),
                new BarChartData.BarSeries("Orders", orderCounts),
                new BarChartData.BarSeries("Payments", payCounts)
            )
        );
    }

    @Cacheable(value = "pageConvertRates", key = "#taskId", unless = "#result == null or #result.getSeries().isEmpty()")
    public LineChartData getPageConversionRate(Long taskId) {
        List<PageConvertRate> rates = pageConvertRateRepository.findByTaskIdOrderByPageFlowAsc(taskId);
        
        if (rates == null || rates.isEmpty()) {
            return new LineChartData("Page Conversion Rate", Collections.emptyList(), Collections.emptyList());
        }

        List<String> pageFlows = rates.stream()
                .map(PageConvertRate::getPageFlow)
                .collect(Collectors.toList());

        // 转化率以百分比形式输出，保留两位小数（避免 int 截断丢精度）
        // 例：0.0084 → 0.84，而非强转 int 后的 1
        List<Double> convertRates = rates.stream()
                .map(rate -> Math.round(rate.getConvertRate() * 10000.0) / 100.0)
                .collect(Collectors.toList());

        return new LineChartData(
            "Page Conversion Rate",
            pageFlows,
            convertRates
        );
    }

    @Cacheable(value = "sessionStats", key = "#taskId + '_summary'", unless = "#result == null")
    public PieChartData getSessionSummary(Long taskId) {
        SessionAggrStat stat = sessionAggrStatRepository.findByTaskId(taskId);
        if (stat == null) {
            return null;
        }

        return new PieChartData(
            Arrays.asList(
                new PieChartData.PieDataItem("1-3s",   stat.getVisitLength1s3s()),
                new PieChartData.PieDataItem("4-6s",   stat.getVisitLength4s6s()),
                new PieChartData.PieDataItem("7-9s",   stat.getVisitLength7s9s()),
                new PieChartData.PieDataItem("10-30s", stat.getVisitLength10s30s()),
                new PieChartData.PieDataItem("30-60s", stat.getVisitLength30s60s()),
                new PieChartData.PieDataItem("1-3m",   stat.getVisitLength1m3m()),
                new PieChartData.PieDataItem("3-10m",  stat.getVisitLength3m10m()),
                new PieChartData.PieDataItem("10-30m", stat.getVisitLength10m30m()),
                new PieChartData.PieDataItem(">30m",   stat.getVisitLength30m())
            )
        );
    }

    @Cacheable(value = "userLevelStats", key = "#taskId", unless = "#result == null or #result.getData().isEmpty()")
    public PieChartData getUserLevelStat(Long taskId) {
        List<UserLevelStat> stats = userLevelStatRepository.findByTaskIdOrderByUserCountDesc(taskId);
        if (stats == null || stats.isEmpty()) {
            return null;
        }
        return new PieChartData(
            stats.stream()
                .map(s -> new PieChartData.PieDataItem(s.getUserLevel(), s.getUserCount()))
                .collect(java.util.stream.Collectors.toList())
        );
    }

    @Cacheable(value = "associationRules", key = "#taskId", unless = "#result == null or #result.getRules().isEmpty()")
    public AssociationRuleData getAssociationRules(Long taskId) {
        List<ProductAssociation> associations = productAssociationRepository.findByTaskIdOrderByConfidenceDescLiftDesc(taskId);
        if (associations == null || associations.isEmpty()) {
            return new AssociationRuleData(Collections.emptyList());
        }
        List<RuleItem> rules = associations.stream()
            .map(a -> new RuleItem(
                a.getAntecedent(),
                a.getConsequent(),
                a.getSupport()    != null ? a.getSupport()    : 0.0,
                a.getConfidence() != null ? a.getConfidence() : 0.0,
                a.getLift()       != null ? a.getLift()       : 0.0
            ))
            .collect(java.util.stream.Collectors.toList());
        return new AssociationRuleData(rules);
    }

    @CacheEvict(value = {"sessionStats", "top10Categories", "pageConvertRates", "userLevelStats", "associationRules"}, allEntries = true)
    public void clearCache() {
    }
}
