package com.roottrace.slo.dto;

import com.roottrace.slo.SloStatus;

import java.util.UUID;

public record ErrorBudgetResponse(
        UUID sloId,
        String serviceName,
        String sloName,
        double targetPercentage,
        double errorBudgetPercentage,
        long totalEvents,
        long allowedBadEvents,
        long actualBadEvents,
        long remainingBadEvents,
        double budgetConsumedPercentage,
        double budgetRemainingPercentage,
        SloStatus status
) {
}
