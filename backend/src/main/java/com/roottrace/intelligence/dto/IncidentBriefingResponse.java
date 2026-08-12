package com.roottrace.intelligence.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentBriefingResponse(
        UUID incidentId,
        String executiveSummary,
        boolean isRecurringIssue,
        int recurrenceCount,
        List<String> recommendedTriageActions,
        List<String> historicalRootCauses,
        List<String> provenInvestigationSteps,
        List<String> pastPostmortemLessons,
        List<String> uncompletedActionItems,
        List<CorrelatedIncidentResponse> topCorrelatedIncidents,
        Instant generatedAt
) {
}
