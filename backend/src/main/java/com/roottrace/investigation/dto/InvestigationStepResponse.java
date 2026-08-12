package com.roottrace.investigation.dto;

import com.roottrace.investigation.InvestigationStepStatus;
import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.UUID;

public record InvestigationStepResponse(
        UUID id,
        int stepOrder,
        String title,
        String description,
        InvestigationStepStatus status,
        String evidence,
        UserDto assignedTo,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
