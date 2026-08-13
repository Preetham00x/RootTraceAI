package com.roottrace.slo.dto;

import java.util.List;

public record ReliabilityTrendResponse(
        String serviceName,
        int windowDays,
        String interval, // "daily", "weekly"
        List<ReliabilityDataPoint> dataPoints
) {
    public record ReliabilityDataPoint(
            String period,
            double sloCompliancePercentage,
            double errorBudgetConsumedPercentage,
            int incidentCount
    ) {
    }
}
