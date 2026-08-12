package com.roottrace.postmortem.dto;

import com.roottrace.postmortem.ActionItemCategory;
import com.roottrace.postmortem.ActionItemPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record CreateActionItemRequest(
        @NotBlank(message = "Title is required")
        String title,
        @NotBlank(message = "Description is required")
        String description,
        ActionItemCategory category,
        ActionItemPriority priority,
        UUID assignedToId,
        Instant dueDate
) {
}
