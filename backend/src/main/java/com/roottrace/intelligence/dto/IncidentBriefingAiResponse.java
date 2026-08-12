package com.roottrace.intelligence.dto;

import java.util.List;

public record IncidentBriefingAiResponse(
        String executiveSummary,
        Boolean isRecurringIssue,
        Integer recurrenceCount,
        List<String> recommendedTriageActions,
        List<String> historicalRootCauses,
        List<String> provenInvestigationSteps,
        List<String> pastPostmortemLessons,
        List<String> uncompletedActionItems
) {
}
