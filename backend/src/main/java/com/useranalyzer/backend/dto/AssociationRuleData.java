package com.useranalyzer.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssociationRuleData {

    private List<RuleItem> rules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        private String antecedent;
        private String consequent;
        private double support;
        private double confidence;
        private double lift;
    }
}
