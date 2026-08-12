package com.roottrace.investigation.dto;

import java.util.List;

public record InvestigationPlanAiResponse(
        String title,
        List<StepAiResponse> steps
) {
    public record StepAiResponse(
            String title,
            String description
    ) {
    }
}
