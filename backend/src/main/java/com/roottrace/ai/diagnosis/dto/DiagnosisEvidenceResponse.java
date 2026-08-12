package com.roottrace.ai.diagnosis.dto;

import java.util.UUID;

public record DiagnosisEvidenceResponse(
        UUID chunkId,
        Double relevanceScore,
        String reason
) {
}
