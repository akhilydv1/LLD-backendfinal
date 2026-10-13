package com.designloop.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAttemptRequest(
        @NotBlank(message = "problemId is required") String problemId,
        String submissionFormat
) {
}
