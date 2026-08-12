package com.roottrace.incident.dto;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentSummaryResponse(
        UUID id,
        String title,
        String service,
        IncidentSeverity severity,
        IncidentStatus status,
        String environment,
        CreatorResponse createdBy,
        Instant createdAt
) {
}
