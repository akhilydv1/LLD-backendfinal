package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.exception.ApiException;
import com.designloop.repository.AttemptRepository;
import com.designloop.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttemptServiceValidationTest {
    @Test
    void staleDraftRevisionReturnsConflict() {
        AttemptRepository attemptRepository = mock(AttemptRepository.class);
        ProblemRepository problemRepository = mock(ProblemRepository.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        EvaluationWorker worker = mock(EvaluationWorker.class);
        AttemptDocument attempt = draftAttempt();
        when(attemptRepository.findByIdAndLearnerId("attempt-1", "learner-1")).thenReturn(Optional.of(attempt));
        AttemptService service = new AttemptService(attemptRepository, problemRepository, attachmentService, worker, new ObjectMapper(), 3);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.saveDraft("learner-1", "attempt-1", new com.designloop.dto.DraftRequest(0,
                        new com.designloop.dto.DraftRequest.Artifact("STRUCTURED_TEXT", new LinkedHashMap<>(), ""))));

        assertEquals(409, exception.getStatus().value());
        assertEquals("REVISION_CONFLICT", exception.getCode());
    }

    @Test
    void incompleteTextCannotBeSubmitted() {
        AttemptRepository attemptRepository = mock(AttemptRepository.class);
        ProblemRepository problemRepository = mock(ProblemRepository.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        EvaluationWorker worker = mock(EvaluationWorker.class);
        AttemptDocument attempt = draftAttempt();
        attempt.setRevision(1);
        attempt.setSubmissionFormat("TEXT");
        attempt.setDraft(new AttemptDocument.DraftArtifact("STRUCTURED_TEXT", new LinkedHashMap<>(), ""));
        when(attemptRepository.findByIdAndLearnerId("attempt-1", "learner-1")).thenReturn(Optional.of(attempt));
        AttemptService service = new AttemptService(attemptRepository, problemRepository, attachmentService, worker, new ObjectMapper(), 3);

        ApiException exception = assertThrows(ApiException.class, () -> service.submit("learner-1", "attempt-1", "key-1"));

        assertEquals(400, exception.getStatus().value());
        assertEquals("INVALID_ARTIFACT", exception.getCode());
    }

    private AttemptDocument draftAttempt() {
        AttemptDocument attempt = new AttemptDocument();
        attempt.setId("attempt-1"); attempt.setLearnerId("learner-1"); attempt.setProblemId("p1");
        attempt.setSubmissionFormat("TEXT"); attempt.setStatus("DRAFT"); attempt.setRevision(1);
        attempt.setDraft(new AttemptDocument.DraftArtifact("STRUCTURED_TEXT", new LinkedHashMap<>(), ""));
        attempt.setAttachments(new java.util.ArrayList<>()); attempt.setIdempotencyKeys(new java.util.ArrayList<>());
        attempt.setTimeline(new java.util.ArrayList<>());
        attempt.setEvaluation(new AttemptDocument.EvaluationMetadata(0, 3, null, null, false, null));
        attempt.setCreatedAt(Instant.now()); attempt.setUpdatedAt(Instant.now());
        return attempt;
    }
}
