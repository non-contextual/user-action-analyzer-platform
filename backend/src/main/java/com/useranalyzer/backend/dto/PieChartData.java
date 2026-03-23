package com.useranalyzer.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PieChartData {
    private List<PieDataItem> data;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PieDataItem {
        private String name;
        private Long value;
    }
}
