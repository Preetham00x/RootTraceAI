package com.roottrace.ai.diagnosis.dto;

import java.time.Instant;
import java.util.UUID;

public record DiagnosisSummaryResponse(
        UUID id,
        String summary,
        Double confidence,
        Instant createdAt
) {
}
