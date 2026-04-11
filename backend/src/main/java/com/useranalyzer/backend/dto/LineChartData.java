package com.useranalyzer.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineChartData {
    private String name;
    @JsonProperty("xAxis")
    private List<String> xAxis;
    private List<? extends Number> series;
}
