package com.useranalyzer.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineChartData {
    private String name;
    private List<String> xAxis;
    private List<? extends Number> series;
}
