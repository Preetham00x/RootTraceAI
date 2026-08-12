package com.roottrace.postmortem.dto;

import java.util.List;

public record PostmortemAiResponse(
        String title,
        String summary,
        String impactSummary,
        String rootCauseAnalysis,
        String resolutionSummary,
        List<String> lessonsLearned,
        List<ProposedActionItem> actionItems
) {
    public record ProposedActionItem(
            String title,
            String description,
            String category,
            String priority
    ) {
    }
}
