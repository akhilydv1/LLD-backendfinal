package com.designloop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DraftRequest(
        @NotNull(message = "expectedRevision is required") Integer expectedRevision,
        @NotNull(message = "artifact is required") @Valid Artifact artifact
) {
    public record Artifact(
            @NotNull(message = "format is required") String format,
            @NotNull(message = "sections are required") java.util.Map<String, String> sections,
            @Size(max = 2000, message = "notes must be at most 2,000 characters") String notes) {
    }
}
