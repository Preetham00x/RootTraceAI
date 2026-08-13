package com.roottrace.slo.dto;

import java.util.List;

public record ReliabilityAdvisorAiResponse(
        String executiveSummary,
        List<String> reliabilityConcerns,
        List<String> recommendedActions,
        String priority // "LOW", "MEDIUM", "HIGH", "CRITICAL"
) {
}
