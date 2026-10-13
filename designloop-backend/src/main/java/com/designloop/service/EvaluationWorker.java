package com.designloop.service;

import com.designloop.domain.AttemptDocument;
import com.designloop.domain.ProblemDocument;
import com.designloop.exception.ApiException;
import com.designloop.repository.AttemptRepository;
import com.designloop.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

@Service
public class EvaluationWorker {
    private static final Logger log = LoggerFactory.getLogger(EvaluationWorker.class);

    private final AttemptRepository attempts;
    private final ProblemRepository problems;
    private final DesignReviewEvaluator evaluator;

    public EvaluationWorker(AttemptRepository attempts, ProblemRepository problems, DesignReviewEvaluator evaluator) {
        this.attempts = attempts;
        this.problems = problems;
        this.evaluator = evaluator;
    }

    @Async("evaluationExecutor")
    public void evaluateAsync(String attemptId, int expectedRun) {
        long started = System.nanoTime();
        try {
            AttemptDocument attempt = attempts.findById(attemptId).orElse(null);
            if (attempt == null || !"SUBMITTED".equals(attempt.getStatus())) return;
            if (attempt.getEvaluation() == null || attempt.getEvaluation().getRunCount() != expectedRun) return;
            if (attempt.getSubmission() == null) {
                throw new IllegalStateException("No frozen submission is available for evaluation.");
            }

            attempt.setStatus("EVALUATING");
            attempt.getEvaluation().setStartedAt(Instant.now());
            attempt.getEvaluation().setRetryAllowed(false);
            appendTimeline(attempt, "EVALUATING", "Evaluation started");
            attempts.save(attempt);

            ProblemDocument problem = problems.findById(attempt.getProblemId()).orElseThrow(
                    () -> new ApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "The problem associated with this attempt no longer exists."));
            AttemptDocument.FeedbackDocument feedback = evaluator.evaluate(problem, attempt.getSubmission(), attempt.getAttachments());
            feedback.setDurationMillis(Math.max(feedback.getDurationMillis(), (System.nanoTime() - started) / 1_000_000));

            // Re-read the current version before finalizing so a stale async worker cannot overwrite a newer retry.
            AttemptDocument current = attempts.findById(attemptId).orElse(null);
            if (current == null || current.getEvaluation() == null
                    || current.getEvaluation().getRunCount() != expectedRun
                    || !"EVALUATING".equals(current.getStatus())) {
                log.info("Ignoring stale evaluation completion for attemptId={} run={}", attemptId, expectedRun);
                return;
            }
            current.setFeedback(feedback);
            current.setFailure(null);
            current.setStatus("COMPLETED");
            current.getEvaluation().setCompletedAt(Instant.now());
            current.getEvaluation().setRetryAllowed(false);
            current.setUpdatedAt(Instant.now());
            appendTimeline(current, "COMPLETED", "Feedback saved by " + feedback.getProvider());
            attempts.save(current);
        } catch (Exception ex) {
            log.warn("Evaluation failed attemptId={} run={} message={}", attemptId, expectedRun, safeLogMessage(ex));
            markFailed(attemptId, expectedRun, ex);
        }
    }

    private void markFailed(String attemptId, int expectedRun, Exception ex) {
        try {
            AttemptDocument current = attempts.findById(attemptId).orElse(null);
            if (current == null || current.getEvaluation() == null
                    || current.getEvaluation().getRunCount() != expectedRun
                    || "COMPLETED".equals(current.getStatus())) {
                return;
            }
            current.setStatus("FAILED");
            current.setFailure(failureMessage(ex));
            current.getEvaluation().setCompletedAt(Instant.now());
            current.getEvaluation().setRetryAllowed(current.getEvaluation().getRunCount() < current.getEvaluation().getMaxRuns());
            current.getEvaluation().setRetryAfter(null);
            current.setUpdatedAt(Instant.now());
            appendTimeline(current, "FAILED", "Evaluation failed; submitted evidence remains saved.");
            attempts.save(current);
        } catch (Exception persistenceFailure) {
            log.error("Unable to persist evaluation failure for attemptId={} run={}", attemptId, expectedRun, persistenceFailure);
        }
    }

    private void appendTimeline(AttemptDocument attempt, String status, String detail) {
        if (attempt.getTimeline() == null) attempt.setTimeline(new ArrayList<>());
        attempt.getTimeline().add(new AttemptDocument.TimelineEvent(status, Instant.now(), attempt.getEvaluation().getRunCount(), detail));
    }

    private String failureMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Evaluation could not complete. Check the server log and retry if a run remains.";
        }
        if (message.toLowerCase().contains("gemini")) {
            return message + " Your submission is safely saved; you may retry if eligible.";
        }
        return "Evaluation could not complete or its response could not be validated. Your submission is safely saved; you may retry if eligible.";
    }

    private String safeLogMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) return ex.getClass().getSimpleName();
        // Avoid accidentally writing query-string API keys into logs.
        return message.replaceAll("(?i)(key=)[^&\\s]+", "$1[REDACTED]");
    }
}
