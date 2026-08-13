package com.roottrace.commandcenter.dto;

import java.time.Instant;
import java.util.List;

public record ExecutiveReliabilityAdvisorResponse(
        String executiveSummary,
        List<String> keyConcerns,
        List<ExecutiveReliabilityAdvisorAiResponse.ServiceAttentionItem> servicesRequiringAttention,
        List<ExecutiveReliabilityAdvisorAiResponse.ExecutiveActionItem> recommendedActions,
        List<String> positiveSignals,
        double overallReliabilityScore,
        String overallRiskTier,
        int activeBreaches,
        Instant generatedAt
) {}
