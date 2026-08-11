package com.roottrace.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveIncidentRequest(

        @NotBlank(message = "Resolution is required")
        String resolution
) {
}
