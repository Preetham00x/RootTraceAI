package com.roottrace.postmortem.dto;

import com.roottrace.postmortem.PostmortemStatus;
import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostmortemResponse(
        UUID id,
        UUID incidentId,
        String title,
        String summary,
        String impactSummary,
        String rootCauseAnalysis,
        String resolutionSummary,
        List<PostmortemTimelineEntry> timeline,
        List<String> lessonsLearned,
        PostmortemStatus status,
        Long downtimeMinutes,
        UserDto createdBy,
        Instant publishedAt,
        List<PostmortemActionItemResponse> actionItems,
        Instant createdAt,
        Instant updatedAt
) {
}
