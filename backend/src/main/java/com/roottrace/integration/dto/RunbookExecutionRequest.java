package com.roottrace.integration.dto;

import com.roottrace.integration.RunbookExecutionStatus;
import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.UUID;

public record RunbookExecutionRequest(
        String command
) {
}
