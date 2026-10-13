package com.designloop.controller;

import com.designloop.service.DesignReviewEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Metadata", description = "Client contract, evaluator mode and service health")
public class MetaController {
    private final DesignReviewEvaluator evaluator;
    private final int maxRuns;
    private final long maxFileSize;
    private final int maxAttachments;
    private final String demoLearnerId;

    public MetaController(DesignReviewEvaluator evaluator,
                          @Value("${designloop.max-evaluation-runs:3}") int maxRuns,
                          @Value("${designloop.max-file-size-bytes:20971520}") long maxFileSize,
                          @Value("${designloop.max-attachments-per-attempt:8}") int maxAttachments,
                          @Value("${designloop.demo-learner-id:d847c7fe-2d42-49d4-8e4d-dde0acbbfd6a}") String demoLearnerId) {
        this.evaluator = evaluator;
        this.maxRuns = maxRuns;
        this.maxFileSize = maxFileSize;
        this.maxAttachments = maxAttachments;
        this.demoLearnerId = demoLearnerId;
    }

    @GetMapping("/meta")
    @Operation(summary = "Get frontend contract, supported input formats and active evaluation mode")
    public Map<String, Object> meta() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "DesignLoop");
        result.put("version", "1.0.0");
        result.put("evaluation", Map.of("maxRuns", maxRuns,
                "active", Map.of("provider", evaluator.activeProvider(), "model", evaluator.activeModel() == null ? "not-configured" : evaluator.activeModel(),
                        "assessmentType", evaluator.isGeminiConfigured() ? "RUBRIC_BASED_DESIGN_REVIEW" : "READINESS_ONLY",
                        "promptVersion", evaluator.isGeminiConfigured() ? "designloop-rubric-v1" : "readiness-v1"),
                "configuredMode", "AUTO"));
        result.put("submissionFormats", List.of("TEXT", "DOCUMENT", "DIAGRAM", "COMBINED"));
        result.put("attachmentPolicy", Map.of("maxFileSizeBytes", maxFileSize, "maxAttachmentsPerAttempt", maxAttachments,
                "documentExtensions", List.of("pdf", "doc", "docx", "txt", "md", "rtf", "odt"),
                "diagramExtensions", List.of("png", "jpg", "jpeg", "webp", "svg", "pdf", "drawio", "xml")));
        result.put("learnerIdentity", Map.of("demoLearnerId", demoLearnerId, "header", "X-Learner-Id", "warning", "Local demo identity only; this header is not authentication and the service must not be publicly exposed without real authentication."));
        result.put("maxTotalCharacters", 36000);
        result.put("sections", List.of(
                section("requirements", "Requirements and assumptions", true, 1, 6000, "Explain the business rules and assumptions. Mention R1, R2, ... where useful; IDs are not mandatory."),
                section("responsibilities", "Classes and responsibilities", true, 1, 6000, "List important classes/interfaces, what each owns and the invariants it protects."),
                section("relationships", "Relationships and dependencies", true, 1, 6000, "Explain composition, references, multiplicity and dependency direction."),
                section("behavior", "Main interaction and failure behaviour", true, 1, 6000, "Walk through one main use case in order, including state transitions and errors."),
                section("tradeoffs", "Trade-offs and change scenario", true, 8, 6000, "Justify simplicity or abstractions and explain how the problem's change request affects this design."),
                section("tests", "Edge cases and test plan", true, 1, 6000, "Describe business-rule tests, expected outcomes, and dependencies you would fake."),
                section("code", "Optional supporting code", false, 0, 12000, "Paste short code only; code is not compiled or executed.")));
        result.put("safetyNote", "No score is returned when Gemini is unavailable. RULES mode performs readiness checks only.");
        return result;
    }

    @GetMapping("/health")
    @Operation(summary = "Lightweight application health check")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "designloop-backend", "evaluator", evaluator.activeProvider());
    }

    private Map<String, Object> section(String key, String label, boolean required, int min, int max, String guidance) {
        return Map.of("key", key, "label", label, "required", required, "minCharacters", min, "maxCharacters", max, "guidance", guidance);
    }
}
