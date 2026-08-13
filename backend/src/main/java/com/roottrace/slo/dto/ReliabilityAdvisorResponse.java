package com.roottrace.slo.dto;

import java.time.Instant;
import java.util.List;

public record ReliabilityAdvisorResponse(
        String serviceName,
        String executiveSummary,
        List<String> reliabilityConcerns,
        List<String> recommendedActions,
        String priority,
        double currentRiskScore,
        String currentRiskTier,
        int activeBreaches,
        Instant generatedAt
) {
}
