package com.roottrace.investigation.dto;

import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvestigationPlanResponse(
        UUID id,
        UUID incidentId,
        UUID sourceDiagnosisId,
        String title,
        UserDto createdBy,
        List<InvestigationStepResponse> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
