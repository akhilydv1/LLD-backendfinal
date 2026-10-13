package com.designloop.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "attempts")
public class AttemptDocument {
    @Id
    private String id;
    @Version
    private Long mongoVersion;
    private String learnerId;
    private String problemId;
    private ProblemSnapshot problem;
    private String submissionFormat;
    private String status = "DRAFT";
    private int revision;
    private DraftArtifact draft = new DraftArtifact();
    private SubmissionSnapshot submission;
    private List<AttachmentDocument> attachments = new ArrayList<>();
    private EvaluationMetadata evaluation = new EvaluationMetadata();
    private FeedbackDocument feedback;
    private List<String> idempotencyKeys = new ArrayList<>();
    private String failure;
    private List<TimelineEvent> timeline = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private String status;
        private Instant at;
        private int evaluationRun;
        private String detail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemSnapshot {
        private String id;
        private String title;
        private String difficulty;
        private int suggestedMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftArtifact {
        private String format = "STRUCTURED_TEXT";
        private Map<String, String> sections = new LinkedHashMap<>();
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionSnapshot {
        private String id;
        private int draftRevision;
        private String fingerprint;
        private Instant submittedAt;
        private DraftArtifact artifact;
        private List<String> attachmentIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDocument {
        private String id;
        private String name;
        private String kind;
        private long size;
        private String contentType;
        private String storagePath;
        private String checksum;
        private String notes;
        private String extractedText;
        private Instant uploadedAt;
        private boolean textExtracted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationMetadata {
        private int runCount;
        private int maxRuns = 3;
        private Instant startedAt;
        private Instant completedAt;
        private boolean retryAllowed;
        private Instant retryAfter;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackDocument {
        private String provider;
        private String model;
        private String assessmentType;
        private String promptVersion;
        private String rubricVersion;
        private Instant generatedAt;
        private long durationMillis;
        private String summary;
        private Integer overallScore;
        private List<CriterionFeedback> criteria = new ArrayList<>();
        private List<String> nextSteps = new ArrayList<>();
        private List<String> limitations = new ArrayList<>();
        private List<ReadinessCheck> deterministicChecks = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionFeedback {
        private String criterionId;
        private String label;
        private Integer score;
        private String confidence;
        private List<EvidenceQuote> evidence = new ArrayList<>();
        private String concern;
        private String suggestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceQuote {
        private String section;
        private String quote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadinessCheck {
        private String code;
        private String level;
        private String message;
    }
}
