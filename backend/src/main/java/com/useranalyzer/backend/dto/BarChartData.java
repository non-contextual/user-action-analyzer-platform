package com.useranalyzer.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BarChartData {
    private List<String> xAxis;
    private List<BarSeries> series;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarSeries {
        private String name;
        private List<Long> data;
    }
}
