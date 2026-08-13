package com.roottrace.commandcenter.dto;

import java.util.List;

public record ServiceHealthSummaryResponse(
        String serviceName,
        double healthScore,
        String riskTier,
        int incidentCount,
        int activeIncidentCount,
        int criticalIncidentCount,
        Double meanMttrMinutes,
        double recurrenceRate,
        int sloCount,
        int healthySloCount,
        int warningSloCount,
        int breachedSloCount,
        double errorBudgetConsumptionPercent,
        int openActionItems,
        int overdueActionItems,
        int failedRunbookExecutions,
        List<String> topRiskFactors
) {}
