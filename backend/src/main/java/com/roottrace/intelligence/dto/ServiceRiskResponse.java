package com.roottrace.intelligence.dto;

import java.time.Instant;
import java.util.List;

public record ServiceRiskResponse(
        String serviceName,
        Double riskScore,
        String riskTier,
        long totalIncidents30d,
        long criticalIncidents30d,
        long highIncidents30d,
        long openIncidentsCount,
        Double recurrenceRate,
        Double averageMttrMinutes,
        long unresolvedActionItemsCount,
        List<String> riskFactors,
        Instant evaluatedAt
) {
}
