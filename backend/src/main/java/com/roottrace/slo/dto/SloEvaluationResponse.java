package com.roottrace.slo.dto;

import com.roottrace.slo.SloStatus;

import java.time.Instant;
import java.util.UUID;

public record SloEvaluationResponse(
        UUID sloId,
        String serviceName,
        String sloName,
        double targetPercentage,
        double actualPercentage,
        double differencePercentage,
        SloStatus status,
        long totalEvents,
        long goodEvents,
        long badEvents,
        double errorBudgetRemainingPercentage,
        double budgetConsumedPercentage,
        Instant evaluatedAt
) {
}
