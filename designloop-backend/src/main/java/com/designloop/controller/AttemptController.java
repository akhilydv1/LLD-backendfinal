package com.designloop.controller;

import com.designloop.domain.AttemptDocument;
import com.designloop.dto.CreateAttemptRequest;
import com.designloop.dto.DraftRequest;
import com.designloop.service.AttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attempts")
@Tag(name = "Learner attempts", description = "Create drafts, upload evidence, submit designs and view feedback")
public class AttemptController {
    private final AttemptService service;
    public AttemptController(AttemptService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create an editable attempt draft")
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("X-Learner-Id") String learnerId,
            @Valid @RequestBody CreateAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAttempt(learnerId, request));
    }

    @PutMapping("/{id}/draft")
    @Operation(summary = "Save draft evidence with revision conflict checking")
    public Map<String, Object> saveDraft(
            @RequestHeader("X-Learner-Id") String learnerId,
            @PathVariable String id,
            @Valid @RequestBody DraftRequest request) {
        return service.saveDraft(learnerId, id, request);
    }

    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Upload a document or diagram to a draft")
    public ResponseEntity<Map<String, Object>> uploadAttachment(
            @RequestHeader("X-Learner-Id") String learnerId,
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind,
            @RequestParam(value = "notes", required = false) String notes) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadAttachment(learnerId, id, file, kind, notes));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Remove an uploaded attachment from an editable draft")
    public ResponseEntity<Void> removeAttachment(
            @RequestHeader("X-Learner-Id") String learnerId,
            @PathVariable String id,
            @PathVariable String attachmentId) {
        service.removeAttachment(learnerId, id, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Freeze a saved submission and queue evaluation asynchronously")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader("X-Learner-Id") String learnerId,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Map<String, Object> result = service.submit(learnerId, id, idempotencyKey);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping
    @Operation(summary = "List the current learner's attempts")
    public List<Map<String, Object>> list(@RequestHeader("X-Learner-Id") String learnerId) {
        return service.listAttempts(learnerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get attempt status, draft, submitted evidence, feedback and timeline")
    public Map<String, Object> get(@RequestHeader("X-Learner-Id") String learnerId, @PathVariable String id) {
        return service.getAttempt(learnerId, id);
    }

    @GetMapping("/{id}/feedback")
    @Operation(summary = "Get completed rubric-based design feedback")
    public AttemptDocument.FeedbackDocument feedback(@RequestHeader("X-Learner-Id") String learnerId, @PathVariable String id) {
        return service.getFeedback(learnerId, id);
    }

    @PostMapping("/{id}/retry-evaluation")
    @Operation(summary = "Retry an eligible failed evaluation using the saved submission")
    public ResponseEntity<Map<String, Object>> retry(@RequestHeader("X-Learner-Id") String learnerId, @PathVariable String id) {
        return ResponseEntity.accepted().body(service.retryEvaluation(learnerId, id));
    }
}
