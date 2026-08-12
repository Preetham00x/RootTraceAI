package com.roottrace.ai.diagnosis.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID diagnosisId,
        UUID userId,
        boolean helpful,
        String comment,
        Instant createdAt
) {
}
