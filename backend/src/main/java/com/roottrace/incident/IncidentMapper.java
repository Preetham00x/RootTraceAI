package com.roottrace.incident;

import com.roottrace.incident.dto.CreateIncidentRequest;
import com.roottrace.incident.dto.CreatorResponse;
import com.roottrace.incident.dto.IncidentResponse;
import com.roottrace.incident.dto.IncidentSummaryResponse;

/**
 * Maps between Incident entity and DTOs.
 * Kept as a utility class to avoid polluting the entity or DTOs with conversion logic.
 */
final class IncidentMapper {

    private IncidentMapper() {
    }

    static Incident toEntity(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setService(request.service());
        incident.setSeverity(request.severity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setEnvironment(request.environment());
        return incident;
    }

    static IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getService(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getEnvironment(),
                new CreatorResponse(incident.getCreatedBy().getId(), incident.getCreatedBy().getDisplayName()),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getResolvedAt(),
                incident.getResolution()
        );
    }

    static IncidentSummaryResponse toSummary(Incident incident) {
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getService(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getEnvironment(),
                new CreatorResponse(incident.getCreatedBy().getId(), incident.getCreatedBy().getDisplayName()),
                incident.getCreatedAt()
        );
    }
}
