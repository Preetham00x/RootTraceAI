package com.roottrace.intelligence.dto;

import java.util.List;

public record IncidentClustersResponse(
        int totalClusters,
        List<IncidentClusterResponse> clusters
) {
}
