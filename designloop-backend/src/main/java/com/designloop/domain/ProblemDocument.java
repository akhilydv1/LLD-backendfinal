package com.designloop.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "problems")
public class ProblemDocument {
    @Id
    private String id;
    private String title;
    private String difficulty;
    private int suggestedMinutes;
    private String description;
    private String category;
    private List<String> skills = new ArrayList<>();
    private List<Requirement> requirements = new ArrayList<>();
    private List<String> assumptions = new ArrayList<>();
    private List<String> outOfScope = new ArrayList<>();
    private String changeScenario;
    private String rubricVersion = "lld-v1";
    private List<RubricCriterion> criteria = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Requirement {
        private String id;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RubricCriterion {
        private String id;
        private String label;
        private int weight;
        private String description;
        private String evidenceSection;
    }
}
