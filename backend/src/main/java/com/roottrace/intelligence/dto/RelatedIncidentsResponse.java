package com.roottrace.intelligence.dto;

import java.util.List;
import java.util.UUID;

public record RelatedIncidentsResponse(
        UUID incidentId,
        int totalFound,
        boolean hasPotentialDuplicates,
        List<CorrelatedIncidentResponse> relatedIncidents
) {
}
