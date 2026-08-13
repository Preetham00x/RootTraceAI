package com.roottrace.slo.dto;

import java.time.Instant;
import java.util.List;

public record ReliabilityRiskResponse(
        String serviceName,
        double riskScore,
        String riskTier, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        int activeSloBreaches,
        double averageBudgetConsumedPercentage,
        int criticalBurnRates,
        int recentCriticalIncidents,
        double recurrenceRate,
        int unresolvedActionItems,
        List<String> riskFactors,
        Instant evaluatedAt
) {
}
