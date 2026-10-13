package com.designloop.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        if (ex.getField() != null) fieldErrors.add(Map.of("field", ex.getField(), "message", ex.getMessage()));
        return response(ex.getStatus(), ex.getCode(), ex.getMessage(), request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError).toList();
        String detail = errors.isEmpty() ? "Request validation failed." : errors.get(0).get("message");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request, errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Required header is missing: " + ex.getHeaderName(), request,
                List.of(Map.of("field", ex.getHeaderName(), "message", "This header is required.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Request body is missing or is not valid JSON for this endpoint.", request,
                List.of(Map.of("field", "body", "message", "Check JSON syntax and the documented request schema.")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingUploadPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_UPLOAD_PART", "Required upload part is missing: " + ex.getRequestPartName(), request,
                List.of(Map.of("field", ex.getRequestPartName(), "message", "This multipart field is required.")));
    }


    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "REVISION_CONFLICT", "This record changed while the request was being saved. Refresh the attempt and retry.", request,
                List.of(Map.of("field", "expectedRevision", "message", "The saved revision is no longer current.")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Upload exceeds the configured file-size limit.", request,
                List.of(Map.of("field", "file", "message", "Choose a smaller file.")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unexpected error requestId={} path={}", requestId, request.getRequestURI(), ex);
        Map<String, Object> body = base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The server could not complete the request. Use the requestId when checking server logs.", request, requestId);
        body.put("fieldErrors", List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.valueOf("application/problem+json")).body(body);
    }

    private Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String detail,
                                                         HttpServletRequest request, List<Map<String, String>> errors) {
        Map<String, Object> body = base(status, code, detail, request, UUID.randomUUID().toString());
        body.put("fieldErrors", errors);
        return ResponseEntity.status(status).contentType(MediaType.valueOf("application/problem+json")).body(body);
    }

    private Map<String, Object> base(HttpStatus status, String code, String detail, HttpServletRequest request, String requestId) {
        return new java.util.LinkedHashMap<>(Map.of(
                "type", "urn:designloop:problem:" + code.toLowerCase().replace('_', '-'),
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", detail,
                "instance", request.getRequestURI(),
                "code", code,
                "requestId", requestId,
                "timestamp", Instant.now().toString()
        ));
    }
}
