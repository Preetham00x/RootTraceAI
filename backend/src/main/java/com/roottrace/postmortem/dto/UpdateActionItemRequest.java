package com.roottrace.postmortem.dto;

import com.roottrace.postmortem.ActionItemCategory;
import com.roottrace.postmortem.ActionItemPriority;
import com.roottrace.postmortem.ActionItemStatus;

import java.time.Instant;
import java.util.UUID;

public record UpdateActionItemRequest(
        String title,
        String description,
        ActionItemCategory category,
        ActionItemPriority priority,
        ActionItemStatus status,
        UUID assignedToId,
        Instant dueDate
) {
}
