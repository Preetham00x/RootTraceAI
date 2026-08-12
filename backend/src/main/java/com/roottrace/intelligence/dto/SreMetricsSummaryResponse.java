package com.roottrace.intelligence.dto;

import java.util.List;
import java.util.Map;

public record SreMetricsSummaryResponse(
        int windowDays,
        long totalIncidents,
        long resolvedIncidents,
        long activeIncidents,
        Double meanTimeToResolveMinutes,
        Double medianTimeToResolveMinutes,
        Double meanTimeToDetectMinutes,
        Double recurrenceRate,
        Long totalDowntimeMinutes,
        Map<String, Long> severityCounts,
        List<ServiceIncidentCount> serviceBreakdown,
        List<RecurringRootCauseCount> topRecurringRootCauses
) {
    public record ServiceIncidentCount(
            String service,
            long count,
            Double percentage
    ) {
    }

    public record RecurringRootCauseCount(
            String rootCause,
            long count
    ) {
    }
}
