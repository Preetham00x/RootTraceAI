package com.roottrace.postmortem.dto;

import com.roottrace.postmortem.ActionItemCategory;
import com.roottrace.postmortem.ActionItemPriority;
import com.roottrace.postmortem.ActionItemStatus;
import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.UUID;

public record PostmortemActionItemResponse(
        UUID id,
        UUID postmortemId,
        String title,
        String description,
        ActionItemCategory category,
        ActionItemPriority priority,
        ActionItemStatus status,
        UserDto assignedTo,
        Instant dueDate,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
