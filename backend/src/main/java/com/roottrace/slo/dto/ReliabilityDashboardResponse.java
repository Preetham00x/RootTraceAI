package com.roottrace.slo.dto;

import java.time.Instant;
import java.util.List;

public record ReliabilityDashboardResponse(
        String serviceName,
        double overallRiskScore,
        String riskTier,
        List<SloEvaluationResponse> slos,
        int activeBreaches,
        double averageBudgetConsumedPercentage,
        double highestBurnRate,
        int recentIncidentCount,
        double recurrenceRate,
        int unresolvedActionItems,
        Instant generatedAt
) {
}
