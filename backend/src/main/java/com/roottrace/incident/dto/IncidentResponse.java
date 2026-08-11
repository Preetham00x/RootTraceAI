package com.roottrace.incident.dto;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        String service,
        IncidentSeverity severity,
        IncidentStatus status,
        String environment,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        String resolution
) {
}
