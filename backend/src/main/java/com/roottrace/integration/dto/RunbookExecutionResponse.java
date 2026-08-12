package com.roottrace.integration.dto;

import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.UUID;

public record RunbookExecutionResponse(
        UUID id,
        UUID incidentId,
        UUID investigationStepId,
        String command,
        RunbookExecutionStatus executionStatus,
        UserDto requestedBy,
        UserDto approvedBy,
        String output,
        String errorOutput,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
