package com.roottrace.ai.diagnosis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotNull(message = "helpful must not be null")
        Boolean helpful,

        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String comment
) {
}
