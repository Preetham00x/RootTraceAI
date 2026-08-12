package com.roottrace.investigation.dto;

import com.roottrace.investigation.InvestigationStepStatus;
import java.util.UUID;

public record UpdateInvestigationStepRequest(
        InvestigationStepStatus status,
        String evidence,
        UUID assignedToId
) {
}
