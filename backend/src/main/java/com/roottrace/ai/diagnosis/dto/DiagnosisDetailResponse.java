package com.roottrace.ai.diagnosis.dto;

import com.roottrace.user.dto.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagnosisDetailResponse(
        UUID id,
        UUID incidentId,
        String summary,
        String probableRootCause,
        Double confidence,
        List<String> contributingFactors,
        List<String> recommendedActions,
        List<DiagnosisEvidenceResponse> evidence,
        List<DiagnosisCitationResponse> citations,
        UserDto createdBy,
        Instant createdAt
) {
}
