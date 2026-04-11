package com.useranalyzer.backend.controller;

import com.useranalyzer.backend.dto.*;
import com.useranalyzer.backend.dto.AssociationRuleData;
import com.useranalyzer.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics API", description = "用户行为分析 API")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/session/length-distribution")
    @Operation(summary = "获取 Session 时长分布", description = "返回 Session 时长分布数据，用于折线图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 1", example = "1")
    public ResponseEntity<ApiResponse<LineChartData>> getSessionLengthDistribution(
            @RequestParam(defaultValue = "1") Long taskId) {
        LineChartData data = analyticsService.getSessionLengthDistribution(taskId);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,"Task not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/session/step-distribution")
    @Operation(summary = "获取 Session 步长分布", description = "返回 Session 步长分布数据，用于柱状图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 1", example = "1")
    public ResponseEntity<ApiResponse<BarChartData>> getStepLengthDistribution(
            @RequestParam(defaultValue = "1") Long taskId) {
        BarChartData data = analyticsService.getStepLengthDistribution(taskId);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,"Task not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/category/top10")
    @Operation(summary = "获取热门品类 Top10", description = "返回热门品类 Top10 数据，用于柱状图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 1", example = "1")
    public ResponseEntity<ApiResponse<BarChartData>> getTop10Categories(
            @RequestParam(defaultValue = "1") Long taskId) {
        BarChartData data = analyticsService.getTop10Categories(taskId);
        if (data == null || data.getSeries().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,"No data found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/page/conversion-rate")
    @Operation(summary = "获取页面转化率", description = "返回页面单跳转化率数据，用于折线图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 1", example = "1")
    public ResponseEntity<ApiResponse<LineChartData>> getPageConversionRate(
            @RequestParam(defaultValue = "1") Long taskId) {
        LineChartData data = analyticsService.getPageConversionRate(taskId);
        if (data == null || data.getSeries().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,"No data found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/session/summary")
    @Operation(summary = "获取 Session 汇总", description = "返回 Session 汇总数据，用于饼图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 1", example = "1")
    public ResponseEntity<ApiResponse<PieChartData>> getSessionSummary(
            @RequestParam(defaultValue = "1") Long taskId) {
        PieChartData data = analyticsService.getSessionSummary(taskId);
        if (data == null || data.getData().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,"No data found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/user/level-distribution")
    @Operation(summary = "获取用户分层分布", description = "返回用户按行为分层（VIP/高价值/潜力/普通/沉默）的分布，用于饼图展示")
    @Parameter(name = "taskId", description = "任务 ID，默认为 4", example = "4")
    public ResponseEntity<ApiResponse<PieChartData>> getUserLevelDistribution(
            @RequestParam(defaultValue = "4") Long taskId) {
        PieChartData data = analyticsService.getUserLevelStat(taskId);
        if (data == null || data.getData().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "No data found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/association/rules")
    @Operation(summary = "获取商品关联规则", description = "返回 FP-Growth 挖掘的商品关联规则，按置信度排序")
    @Parameter(name = "taskId", description = "任务 ID，默认为 5", example = "5")
    public ResponseEntity<ApiResponse<AssociationRuleData>> getAssociationRules(
            @RequestParam(defaultValue = "5") Long taskId) {
        AssociationRuleData data = analyticsService.getAssociationRules(taskId);
        if (data == null || data.getRules().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "No data found"));
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @DeleteMapping("/cache/clear")
    @Operation(summary = "清除缓存", description = "清除所有缓存数据")
    public ResponseEntity<ApiResponse<String>> clearCache() {
        analyticsService.clearCache();
        return ResponseEntity.ok(ApiResponse.success("Cache cleared successfully"));
    }
}
