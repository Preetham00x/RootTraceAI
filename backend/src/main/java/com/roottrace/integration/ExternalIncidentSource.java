package com.roottrace.integration;

import com.roottrace.incident.IncidentSeverity;

import java.util.Map;

public interface ExternalIncidentSource {

    record NormalizedAlert(
            String externalEventId,
            String title,
            String description,
            String service,
            String environment,
            IncidentSeverity severity,
            String status, // "firing", "resolved"
            Map<String, String> labels,
            Map<String, String> annotations
    ) {}

    String getProvider();
}
