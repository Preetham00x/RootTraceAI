package com.roottrace.incident.dto;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import jakarta.validation.constraints.Size;

public record UpdateIncidentRequest(

        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        String description,

        @Size(max = 255, message = "Service must not exceed 255 characters")
        String service,

        IncidentSeverity severity,

        IncidentStatus status,

        @Size(max = 100, message = "Environment must not exceed 100 characters")
        String environment
) {
}
