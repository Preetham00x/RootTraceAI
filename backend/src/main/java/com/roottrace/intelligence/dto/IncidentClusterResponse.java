package com.roottrace.intelligence.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentClusterResponse(
        String clusterId,
        String service,
        String title,
        int incidentCount,
        Instant latestIncidentAt,
        Double averageMttrMinutes,
        List<UUID> sampleIncidentIds,
        String primaryRootCause,
        boolean hasOpenActionItems
) {
}
