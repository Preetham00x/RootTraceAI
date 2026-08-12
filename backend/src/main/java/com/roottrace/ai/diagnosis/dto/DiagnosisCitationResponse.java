package com.roottrace.ai.diagnosis.dto;

import java.util.UUID;

public record DiagnosisCitationResponse(
        UUID documentId,
        String documentTitle,
        String sectionPath
) {
}
