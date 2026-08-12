package com.roottrace.investigation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record CreateInvestigationPlanRequest(
        @NotBlank(message = "Plan title is required")
        String title,
        UUID sourceDiagnosisId,
        @NotEmpty(message = "At least one step is required")
        @Valid
        List<CreateInvestigationStepRequest> steps
) {
    public record CreateInvestigationStepRequest(
            @NotBlank(message = "Step title is required")
            String title,
            @NotBlank(message = "Step description is required")
            String description,
            UUID assignedToId
    ) {
    }
}
