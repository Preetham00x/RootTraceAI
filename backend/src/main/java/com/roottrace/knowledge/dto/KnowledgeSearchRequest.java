package com.roottrace.knowledge.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank(message = "Query must not be blank")
        @Size(max = 1000, message = "Query must not exceed 1000 characters")
        String query,

        @Min(value = 1, message = "topK must be at least 1")
        Integer topK,

        String service,
        String environment
) {
    public int effectiveTopK(int defaultTopK) {
        return topK != null && topK > 0 ? topK : defaultTopK;
    }
}
