package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.domain.ProblemDocument;
import com.designloop.dto.CreateAttemptRequest;
import com.designloop.dto.DraftRequest;
import com.designloop.exception.ApiException;
import com.designloop.repository.AttemptRepository;
import com.designloop.repository.ProblemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttemptService {
    private static final Set<String> MODES = Set.of("TEXT", "DOCUMENT", "DIAGRAM", "COMBINED");
    private static final List<String> REQUIRED_TEXT_SECTIONS = List.of("requirements", "responsibilities", "relationships", "behavior", "tradeoffs", "tests");

    private final AttemptRepository attempts;
    private final ProblemRepository problems;
    private final AttachmentService attachmentService;
    private final EvaluationWorker evaluationWorker;
    private final ObjectMapper objectMapper;
    private final int maxRuns;

    public AttemptService(AttemptRepository attempts, ProblemRepository problems, AttachmentService attachmentService,
                          EvaluationWorker evaluationWorker, ObjectMapper objectMapper,
                          @Value("${designloop.max-evaluation-runs:3}") int maxRuns) {
        this.attempts = attempts;
        this.problems = problems;
        this.attachmentService = attachmentService;
        this.evaluationWorker = evaluationWorker;
        this.objectMapper = objectMapper;
        this.maxRuns = Math.max(1, maxRuns);
    }

    public Map<String, Object> createAttempt(String learnerId, CreateAttemptRequest request) {
        String learner = normalizeLearnerId(learnerId);
        ProblemDocument problem = problems.findById(request.problemId()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "No problem exists with id '" + request.problemId() + "'.", "problemId"));
        String mode = normalizeMode(request.submissionFormat());
        AttemptDocument attempt = new AttemptDocument();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setLearnerId(learner);
        attempt.setProblemId(problem.getId());
        attempt.setProblem(new AttemptDocument.ProblemSnapshot(problem.getId(), problem.getTitle(), problem.getDifficulty(), problem.getSuggestedMinutes()));
        attempt.setSubmissionFormat(mode);
        attempt.setStatus("DRAFT");
        attempt.setRevision(0);
        attempt.setDraft(new AttemptDocument.DraftArtifact(mode.equals("TEXT") ? "STRUCTURED_TEXT" : mode, new LinkedHashMap<>(), ""));
        attempt.setEvaluation(new AttemptDocument.EvaluationMetadata(0, maxRuns, null, null, false, null));
        attempt.setCreatedAt(Instant.now());
        attempt.setUpdatedAt(Instant.now());
        attempt.setTimeline(new ArrayList<>(List.of(new AttemptDocument.TimelineEvent("DRAFT", Instant.now(), 0, "Practice started"))));
        return AttemptViewMapper.toMap(attempts.save(attempt));
    }

    public Map<String, Object> saveDraft(String learnerId, String attemptId, DraftRequest request) {
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if (!"DRAFT".equals(attempt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_FROZEN", "Submitted attempts are frozen. Create a new attempt to revise this design.");
        }
        if (request.expectedRevision() != attempt.getRevision()) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT",
                    "This draft changed after you opened it. Refresh the attempt and retry with the latest revision.", "expectedRevision");
        }
        DraftRequest.Artifact supplied = request.artifact();
        String artifactFormat = normalizeArtifactFormat(supplied.format(), attempt.getSubmissionFormat());
        Map<String, String> sections = supplied.sections() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(supplied.sections());
        for (String key : sections.keySet()) {
            if (!Set.of("requirements", "responsibilities", "relationships", "behavior", "tradeoffs", "tests", "code").contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "Unknown section key: " + key + ". Use requirements, responsibilities, relationships, behavior, tradeoffs, tests, or code.", "artifact.sections");
            }
        }
        int totalChars = sections.values().stream().filter(java.util.Objects::nonNull).mapToInt(String::length).sum();
        if (totalChars > 36000) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "Draft exceeds the 36,000 character total limit.", "artifact.sections");
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            int max = "code".equals(entry.getKey()) ? 12000 : 6000;
            if (entry.getValue() != null && entry.getValue().length() > max) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "Section '" + entry.getKey() + "' exceeds " + max + " characters.", "artifact.sections." + entry.getKey());
            }
        }
        attempt.setDraft(new AttemptDocument.DraftArtifact(artifactFormat, sections, supplied.notes() == null ? "" : supplied.notes()));
        attempt.setRevision(attempt.getRevision() + 1);
        attempt.setUpdatedAt(Instant.now());
        appendTimeline(attempt, "DRAFT", "Draft saved at revision " + attempt.getRevision());
        return AttemptViewMapper.toMap(attempts.save(attempt));
    }

    public Map<String, Object> uploadAttachment(String learnerId, String attemptId, MultipartFile file, String kind, String notes) {
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if (!"DRAFT".equals(attempt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_FROZEN", "Attachments cannot be added after submission. Create a new attempt to change evidence.");
        }
        String normalizedKind = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if ("TEXT".equals(attempt.getSubmissionFormat())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_MODE_MISMATCH",
                    "TEXT attempts do not accept attachments. Choose DOCUMENT, DIAGRAM, or COMBINED mode.", "kind");
        }
        if ("DOCUMENT".equals(attempt.getSubmissionFormat()) && !"DOCUMENT".equals(normalizedKind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_MODE_MISMATCH", "Document-only attempts accept DOCUMENT attachments.", "kind");
        }
        if ("DIAGRAM".equals(attempt.getSubmissionFormat()) && !"DIAGRAM".equals(normalizedKind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_MODE_MISMATCH", "Diagram-only attempts accept DIAGRAM attachments.", "kind");
        }
        AttemptDocument.AttachmentDocument attachment = attachmentService.store(attempt, file, normalizedKind, notes);
        attempt.getAttachments().add(attachment);
        attempt.setUpdatedAt(Instant.now());
        appendTimeline(attempt, "DRAFT", "Uploaded " + normalizedKind.toLowerCase(Locale.ROOT) + " evidence: " + attachment.getName());
        attempts.save(attempt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", attachment.getId());
        result.put("name", attachment.getName());
        result.put("kind", attachment.getKind());
        result.put("size", attachment.getSize());
        result.put("contentType", attachment.getContentType());
        result.put("uploadedAt", attachment.getUploadedAt());
        result.put("textExtracted", attachment.isTextExtracted());
        result.put("uploaded", true);
        return result;
    }

    public void removeAttachment(String learnerId, String attemptId, String attachmentId) {
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if (!"DRAFT".equals(attempt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_FROZEN", "Attachments cannot be removed after submission.");
        }
        AttemptDocument.AttachmentDocument target = attempt.getAttachments().stream()
                .filter(item -> item.getId().equals(attachmentId)).findFirst().orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "No attachment with that id exists for this attempt."));
        attempt.getAttachments().removeIf(item -> item.getId().equals(attachmentId));
        attempt.setUpdatedAt(Instant.now());
        appendTimeline(attempt, "DRAFT", "Removed attachment: " + target.getName());
        attempts.save(attempt);
        attachmentService.deleteStoredFileBestEffort(target);
    }

    public Map<String, Object> submit(String learnerId, String attemptId, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank() || key.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be a non-empty string of at most 200 characters.", "Idempotency-Key");
        }
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if (attempt.getIdempotencyKeys().contains(key)) return submitResponse(attempt, true);
        if (!"DRAFT".equals(attempt.getStatus())) {
            // A submitted attempt is immutable; a new key must not re-run the same evaluation.
            return submitResponse(attempt, true);
        }
        validateForSubmission(attempt);
        AttemptDocument.DraftArtifact frozenArtifact = copyArtifact(attempt.getDraft());
        AttemptDocument.SubmissionSnapshot snapshot = new AttemptDocument.SubmissionSnapshot();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setDraftRevision(attempt.getRevision());
        snapshot.setFingerprint(fingerprint(frozenArtifact, attempt.getAttachments()));
        snapshot.setSubmittedAt(Instant.now());
        snapshot.setArtifact(frozenArtifact);
        snapshot.setAttachmentIds(attempt.getAttachments().stream().map(AttemptDocument.AttachmentDocument::getId).toList());
        attempt.setSubmission(snapshot);
        attempt.setStatus("SUBMITTED");
        attempt.getIdempotencyKeys().add(key);
        attempt.getEvaluation().setRunCount(1);
        attempt.getEvaluation().setMaxRuns(maxRuns);
        attempt.getEvaluation().setRetryAllowed(false);
        attempt.getEvaluation().setStartedAt(null);
        attempt.getEvaluation().setCompletedAt(null);
        attempt.setFeedback(null);
        attempt.setFailure(null);
        attempt.setUpdatedAt(Instant.now());
        appendTimeline(attempt, "SUBMITTED", "Submission saved; waiting for evaluation");
        AttemptDocument saved = attempts.save(attempt);
        int run = saved.getEvaluation().getRunCount();
        evaluationWorker.evaluateAsync(saved.getId(), run);
        return submitResponse(saved, false);
    }

    public Map<String, Object> retryEvaluation(String learnerId, String attemptId) {
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if (!"FAILED".equals(attempt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RETRY_NOT_ALLOWED", "Only a failed evaluation can be retried.");
        }
        if (attempt.getEvaluation().getRunCount() >= attempt.getEvaluation().getMaxRuns()) {
            throw new ApiException(HttpStatus.CONFLICT, "RETRY_LIMIT_REACHED", "The maximum number of evaluation runs has been reached.");
        }
        attempt.getEvaluation().setRunCount(attempt.getEvaluation().getRunCount() + 1);
        attempt.getEvaluation().setRetryAllowed(false);
        attempt.getEvaluation().setStartedAt(null);
        attempt.getEvaluation().setCompletedAt(null);
        attempt.setStatus("SUBMITTED");
        attempt.setFailure(null);
        attempt.setFeedback(null);
        attempt.setUpdatedAt(Instant.now());
        appendTimeline(attempt, "SUBMITTED", "Evaluation retry queued; saved submission reused");
        AttemptDocument saved = attempts.save(attempt);
        evaluationWorker.evaluateAsync(saved.getId(), saved.getEvaluation().getRunCount());
        return AttemptViewMapper.toMap(saved);
    }

    public Map<String, Object> getAttempt(String learnerId, String attemptId) {
        return AttemptViewMapper.toMap(ownedAttempt(learnerId, attemptId));
    }

    public List<Map<String, Object>> listAttempts(String learnerId) {
        return attempts.findByLearnerIdOrderByUpdatedAtDesc(normalizeLearnerId(learnerId)).stream().map(AttemptViewMapper::toMap).toList();
    }

    public AttemptDocument.FeedbackDocument getFeedback(String learnerId, String attemptId) {
        AttemptDocument attempt = ownedAttempt(learnerId, attemptId);
        if ("COMPLETED".equals(attempt.getStatus()) && attempt.getFeedback() != null) return attempt.getFeedback();
        if ("FAILED".equals(attempt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "EVALUATION_FAILED", "Evaluation failed. Check the attempt status and retry if another run is available.");
        }
        throw new ApiException(HttpStatus.CONFLICT, "EVALUATION_PENDING", "Evaluation is not complete yet. Refresh the status and request feedback again.");
    }

    public AttemptDocument ownedAttempt(String learnerId, String attemptId) {
        String learner = normalizeLearnerId(learnerId);
        return attempts.findByIdAndLearnerId(attemptId, learner).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "No attempt with that id exists for this learner."));
    }

    private void validateForSubmission(AttemptDocument attempt) {
        AttemptDocument.DraftArtifact draft = attempt.getDraft();
        Map<String, String> sections = draft.getSections() == null ? Map.of() : draft.getSections();
        boolean hasDocument = attempt.getAttachments().stream().anyMatch(a -> "DOCUMENT".equals(a.getKind()));
        boolean hasDiagram = attempt.getAttachments().stream().anyMatch(a -> "DIAGRAM".equals(a.getKind()));
        switch (attempt.getSubmissionFormat()) {
            case "TEXT" -> requireTextSections(sections);
            case "DOCUMENT" -> {
                if (!hasDocument) throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_REQUIRED", "Upload at least one document before submitting.", "attachments");
            }
            case "DIAGRAM" -> {
                if (!hasDiagram) throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_REQUIRED", "Upload at least one diagram before submitting.", "attachments");
            }
            case "COMBINED" -> {
                requireTextSections(sections);
                if (!hasDocument && !hasDiagram) throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_REQUIRED", "Combined mode requires text and at least one document or diagram.", "attachments");
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBMISSION_FORMAT", "Unsupported submission format.", "submissionFormat");
        }
    }

    private void requireTextSections(Map<String, String> sections) {
        Map<String, Integer> minimums = Map.of(
                "requirements", 1,
                "responsibilities", 1,
                "relationships", 1,
                "behavior", 1,
                "tradeoffs", 1,
                "tests", 1
        );
        List<String> missing = REQUIRED_TEXT_SECTIONS.stream()
                .filter(key -> sections.get(key) == null || sections.get(key).isBlank()).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT",
                    "Required text sections are missing: " + String.join(", ", missing) + ".", "artifact.sections");
        }
        List<String> tooShort = REQUIRED_TEXT_SECTIONS.stream()
                .filter(key -> sections.get(key).trim().length() < minimums.get(key))
                .map(key -> key + " (minimum " + minimums.get(key) + " characters)").toList();
        if (!tooShort.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT",
                    "These sections need more detail: " + String.join(", ", tooShort) + ".", "artifact.sections");
        }
    }

    private String normalizeLearnerId(String value) {
        String learner = value == null ? "" : value.trim();
        if (learner.isBlank() || learner.length() > 120 || learner.contains("\n") || learner.contains("\r")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEARNER_ID", "X-Learner-Id must be a non-empty string of at most 120 characters.", "X-Learner-Id");
        }
        return learner;
    }

    private String normalizeMode(String raw) {
        String mode = raw == null || raw.isBlank() ? "TEXT" : raw.trim().toUpperCase(Locale.ROOT);
        if (mode.equals("STRUCTURED_TEXT")) mode = "TEXT";
        if (!MODES.contains(mode)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBMISSION_FORMAT", "submissionFormat must be TEXT, DOCUMENT, DIAGRAM, or COMBINED.", "submissionFormat");
        return mode;
    }

    private String normalizeArtifactFormat(String raw, String mode) {
        String format = raw == null || raw.isBlank() ? mode : raw.trim().toUpperCase(Locale.ROOT);
        if (format.equals("TEXT")) format = "STRUCTURED_TEXT";
        if (!Set.of("STRUCTURED_TEXT", "DOCUMENT", "DIAGRAM", "COMBINED").contains(format)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "artifact.format must be STRUCTURED_TEXT, DOCUMENT, DIAGRAM, or COMBINED.", "artifact.format");
        }
        String expected = switch (mode) {
            case "TEXT" -> "STRUCTURED_TEXT";
            case "DOCUMENT", "DIAGRAM", "COMBINED" -> mode;
            default -> "";
        };
        if (!format.equals(expected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "artifact.format must match the attempt's submissionFormat (" + expected + ").", "artifact.format");
        }
        return format;
    }

    private AttemptDocument.DraftArtifact copyArtifact(AttemptDocument.DraftArtifact source) {
        Map<String, String> sections = source.getSections() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getSections());
        return new AttemptDocument.DraftArtifact(source.getFormat(), sections, source.getNotes());
    }

    private String fingerprint(AttemptDocument.DraftArtifact artifact, List<AttemptDocument.AttachmentDocument> attachments) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("artifact", artifact);
            value.put("attachments", attachments.stream().map(a -> Map.of("id", a.getId(), "checksum", a.getChecksum())).toList());
            byte[] bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to fingerprint submission.", ex);
        }
    }

    private Map<String, Object> submitResponse(AttemptDocument attempt, boolean replayed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("status", attempt.getStatus());
        result.put("revision", attempt.getRevision());
        result.put("evaluationRun", attempt.getEvaluation().getRunCount());
        result.put("replayed", replayed);
        result.put("statusUrl", "/api/v1/attempts/" + attempt.getId());
        result.put("feedbackUrl", "/api/v1/attempts/" + attempt.getId() + "/feedback");
        return result;
    }

    private void appendTimeline(AttemptDocument attempt, String status, String detail) {
        if (attempt.getTimeline() == null) attempt.setTimeline(new ArrayList<>());
        attempt.getTimeline().add(new AttemptDocument.TimelineEvent(status, Instant.now(), attempt.getEvaluation().getRunCount(), detail));
    }
}
