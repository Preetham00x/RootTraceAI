package com.roottrace.incident.dto;

import com.roottrace.incident.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @NotBlank(message = "Service is required")
        @Size(max = 255, message = "Service must not exceed 255 characters")
        String service,

        @NotNull(message = "Severity is required")
        IncidentSeverity severity,

        @Size(max = 100, message = "Environment must not exceed 100 characters")
        String environment,

        @NotBlank(message = "Created by is required")
        @Size(max = 255, message = "Created by must not exceed 255 characters")
        String createdBy
) {
}
