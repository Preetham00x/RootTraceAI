package com.roottrace.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusAlertPayload(
        String version,
        String groupKey,
        String status, // "firing" or "resolved"
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        String externalURL,
        List<PrometheusAlertItem> alerts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrometheusAlertItem(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt,
            String endsAt,
            String generatorURL,
            String fingerprint
    ) {
    }
}
