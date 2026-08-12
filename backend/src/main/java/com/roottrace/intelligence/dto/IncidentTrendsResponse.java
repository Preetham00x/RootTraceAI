package com.roottrace.intelligence.dto;

import java.util.List;

public record IncidentTrendsResponse(
        int windowDays,
        String interval,
        List<TrendDataPoint> dataPoints
) {
    public record TrendDataPoint(
            String period,
            long incidentCount,
            long criticalCount,
            long highCount,
            Double averageMttrMinutes
    ) {
    }
}
