package com.roottrace.intelligence.dto;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record CorrelatedIncidentResponse(
        UUID id,
        String title,
        String service,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant createdAt,
        Instant resolvedAt,
        String resolution,
        Double semanticSimilarity,
        Boolean isSameService,
        Double temporalDistanceHours,
        Double compositeScore,
        Boolean isDuplicateCandidate,
        String correlationReason
) {
}
