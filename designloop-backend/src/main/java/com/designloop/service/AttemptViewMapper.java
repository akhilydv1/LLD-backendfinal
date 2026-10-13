package com.designloop.service;

import com.designloop.domain.AttemptDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttemptViewMapper {
    private AttemptViewMapper() { }

    public static Map<String, Object> toMap(AttemptDocument attempt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", attempt.getId());
        result.put("attemptId", attempt.getId());
        result.put("problemId", attempt.getProblemId());
        result.put("problem", attempt.getProblem());
        result.put("submissionFormat", attempt.getSubmissionFormat());
        result.put("status", attempt.getStatus());
        result.put("revision", attempt.getRevision());
        result.put("draft", artifactMap(attempt.getDraft()));
        result.put("submission", submissionMap(attempt.getSubmission()));
        result.put("attachments", attachmentMaps(attempt.getAttachments()));
        result.put("evaluation", attempt.getEvaluation());
        result.put("feedback", attempt.getFeedback());
        result.put("failure", failureMap(attempt));
        result.put("createdAt", attempt.getCreatedAt());
        result.put("updatedAt", attempt.getUpdatedAt());
        result.put("timeline", attempt.getTimeline() == null ? List.of() : attempt.getTimeline());
        return result;
    }

    public static Map<String, Object> artifactMap(AttemptDocument.DraftArtifact artifact) {
        if (artifact == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", artifact.getFormat());
        result.put("sections", artifact.getSections());
        result.put("notes", artifact.getNotes());
        return result;
    }

    private static Map<String, Object> submissionMap(AttemptDocument.SubmissionSnapshot submission) {
        if (submission == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", submission.getId());
        result.put("draftRevision", submission.getDraftRevision());
        result.put("fingerprint", submission.getFingerprint());
        result.put("submittedAt", submission.getSubmittedAt());
        result.put("artifact", artifactMap(submission.getArtifact()));
        result.put("attachmentIds", submission.getAttachmentIds());
        return result;
    }

    private static List<Map<String, Object>> attachmentMaps(List<AttemptDocument.AttachmentDocument> attachments) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (attachments == null) return result;
        for (AttemptDocument.AttachmentDocument attachment : attachments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", attachment.getId());
            item.put("name", attachment.getName());
            item.put("kind", attachment.getKind());
            item.put("size", attachment.getSize());
            item.put("contentType", attachment.getContentType());
            item.put("notes", attachment.getNotes());
            item.put("uploadedAt", attachment.getUploadedAt());
            item.put("textExtracted", attachment.isTextExtracted());
            item.put("uploaded", true);
            result.add(item);
        }
        return result;
    }

    private static Map<String, Object> failureMap(AttemptDocument attempt) {
        if (attempt.getFailure() == null || attempt.getFailure().isBlank()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", "EVALUATION_FAILED");
        result.put("message", attempt.getFailure());
        return result;
    }
}
