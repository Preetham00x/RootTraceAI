package com.roottrace.investigation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GenerateInvestigationPlanRequest(
        @NotNull(message = "Diagnosis ID is required")
        UUID diagnosisId
) {
}
