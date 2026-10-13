package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.domain.ProblemDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignReviewEvaluatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readinessOnlyModeNeverInventsDesignScore() throws Exception {
        GeminiClient gemini = new GeminiClient("", "gemini-test", "https://example.invalid", 1, 5);
        AttachmentService attachments = new AttachmentService(Files.createTempDirectory("designloop-test").toString(), 20_000_000L, 40_000, 8);
        DesignReviewEvaluator evaluator = new DesignReviewEvaluator(gemini, attachments, mapper);
        ProblemDocument problem = problem();
        AttemptDocument.SubmissionSnapshot submission = submission();

        AttemptDocument.FeedbackDocument feedback = evaluator.evaluate(problem, submission, List.of());

        assertEquals("RULES", feedback.getProvider());
        assertEquals("READINESS_ONLY", feedback.getAssessmentType());
        assertNull(feedback.getOverallScore());
        assertTrue(feedback.getCriteria().stream().allMatch(c -> c.getScore() == null));
        assertTrue(feedback.getLimitations().get(0).contains("not been assessed"));
    }

    @Test
    void validGeminiRubricIsMappedAndWeightedIntoHundredPointScore() throws Exception {
        GeminiClient gemini = mock(GeminiClient.class);
        AttachmentService attachments = mock(AttachmentService.class);
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.getModel()).thenReturn("gemini-test-model");
        when(attachments.buildEvidence(anyList())).thenReturn(List.of());
        when(gemini.generateReview(anyString(), anyList())).thenReturn(modelFeedback(3));
        DesignReviewEvaluator evaluator = new DesignReviewEvaluator(gemini, attachments, mapper);

        AttemptDocument.FeedbackDocument feedback = evaluator.evaluate(problem(), submission(), List.of());

        assertEquals("GEMINI", feedback.getProvider());
        assertEquals("RUBRIC_BASED_DESIGN_REVIEW", feedback.getAssessmentType());
        assertEquals(75, feedback.getOverallScore());
        assertEquals(8, feedback.getCriteria().size());
        assertTrue(feedback.getCriteria().stream().allMatch(c -> c.getScore() == 3));
        assertEquals("Test requirements", feedback.getCriteria().get(0).getEvidence().get(0).getQuote());
        assertTrue(feedback.getCriteria().get(1).getEvidence().isEmpty(), "Invented evidence quotes must be discarded.");
        assertNotNull(feedback.getGeneratedAt());
    }

    @Test
    void invalidGeminiScoresAreRejectedRatherThanReportedAsValidFeedback() throws Exception {
        GeminiClient gemini = mock(GeminiClient.class);
        AttachmentService attachments = mock(AttachmentService.class);
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.getModel()).thenReturn("gemini-test-model");
        when(attachments.buildEvidence(anyList())).thenReturn(List.of());
        when(gemini.generateReview(anyString(), anyList())).thenReturn(modelFeedback(9));
        DesignReviewEvaluator evaluator = new DesignReviewEvaluator(gemini, attachments, mapper);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> evaluator.evaluate(problem(), submission(), List.of()));
        assertTrue(exception.getMessage().contains("validated against the evaluation rubric"));
    }

    private ProblemDocument problem() {
        ProblemDocument p = new ProblemDocument();
        p.setId("test-problem"); p.setTitle("Test Problem"); p.setDescription("A sample design problem.");
        p.setRubricVersion("lld-v1");
        p.setRequirements(List.of(new ProblemDocument.Requirement("R1", "Explain a requirement.")));
        p.setAssumptions(List.of("One process.")); p.setOutOfScope(List.of("Distributed deployment."));
        p.setChangeScenario("Add a new policy.");
        p.setCriteria(List.of(
                new ProblemDocument.RubricCriterion("REQUIREMENTS", "Requirement understanding", 15, "Understand rules.", "requirements"),
                new ProblemDocument.RubricCriterion("RESPONSIBILITIES", "Class responsibilities", 20, "Assign responsibilities.", "responsibilities"),
                new ProblemDocument.RubricCriterion("COUPLING_COHESION", "Coupling and cohesion", 15, "Model relationships.", "relationships"),
                new ProblemDocument.RubricCriterion("ENCAPSULATION", "Encapsulation and interfaces", 10, "Protect invariants.", "behavior"),
                new ProblemDocument.RubricCriterion("ABSTRACTION", "Appropriate abstraction", 10, "Justify abstraction.", "tradeoffs"),
                new ProblemDocument.RubricCriterion("EXTENSIBILITY", "Extensibility", 10, "Handle change.", "tradeoffs"),
                new ProblemDocument.RubricCriterion("EDGE_CASES_TESTABILITY", "Edge cases and testability", 15, "Test failures.", "tests"),
                new ProblemDocument.RubricCriterion("EXPLANATION", "Quality of explanation", 5, "Explain behaviour.", "behavior")
        ));
        return p;
    }

    private AttemptDocument.SubmissionSnapshot submission() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("requirements", "Test requirements");
        sections.put("responsibilities", "Test responsibilities");
        sections.put("relationships", "Test relationships");
        sections.put("behavior", "Test behaviour");
        sections.put("tradeoffs", "Test trade-offs");
        sections.put("tests", "Test plan");
        AttemptDocument.DraftArtifact artifact = new AttemptDocument.DraftArtifact("STRUCTURED_TEXT", sections, "Notes");
        return new AttemptDocument.SubmissionSnapshot("submission-id", 1, "fingerprint", Instant.now(), artifact, new ArrayList<>());
    }

    private String modelFeedback(int score) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("summary", "The design is reasonably explained, with several trade-offs to review.");
        ArrayNode criteria = root.putArray("criteria");
        for (String id : List.of("REQUIREMENTS", "RESPONSIBILITIES", "COUPLING_COHESION", "ENCAPSULATION", "ABSTRACTION", "EXTENSIBILITY", "EDGE_CASES_TESTABILITY", "EXPLANATION")) {
            ObjectNode item = criteria.addObject();
            item.put("criterionId", id); item.put("label", id); item.put("score", score); item.put("confidence", "MEDIUM");
            ArrayNode evidence = item.putArray("evidence");
            if (id.equals("REQUIREMENTS")) {
                ObjectNode quote = evidence.addObject(); quote.put("section", "requirements"); quote.put("quote", "Test requirements");
            }
            if (id.equals("RESPONSIBILITIES")) {
                ObjectNode quote = evidence.addObject(); quote.put("section", "responsibilities"); quote.put("quote", "An invented quote that the learner did not write.");
            }
            item.put("concern", "Could be more specific."); item.put("suggestion", "Add a concrete interaction example.");
        }
        root.putArray("nextSteps").add("Add a failure-flow example.");
        return mapper.writeValueAsString(root);
    }
}
