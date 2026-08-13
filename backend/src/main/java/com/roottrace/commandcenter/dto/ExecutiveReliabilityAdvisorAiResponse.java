package com.roottrace.commandcenter.dto;

import java.util.List;

public record ExecutiveReliabilityAdvisorAiResponse(
        String executiveSummary,
        List<String> keyConcerns,
        List<ServiceAttentionItem> servicesRequiringAttention,
        List<ExecutiveActionItem> recommendedActions,
        List<String> positiveSignals
) {
    public record ServiceAttentionItem(
            String serviceName,
            String reason,
            String priority
    ) {}

    public record ExecutiveActionItem(
            String action,
            String reason,
            String priority
    ) {}
}
