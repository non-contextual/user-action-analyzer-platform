package com.useranalyzer.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BarChartData {
    @JsonProperty("xAxis")
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
