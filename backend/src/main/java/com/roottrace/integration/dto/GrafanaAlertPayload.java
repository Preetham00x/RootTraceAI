package com.roottrace.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GrafanaAlertPayload(
        String title,
        String state, // "alerting", "ok", "paused", "pending"
        String message,
        String ruleId,
        String ruleName,
        String ruleUrl,
        String orgId,
        String dashboardId,
        String panelId,
        Map<String, String> tags,
        List<GrafanaEvalMatch> evalMatches,
        List<GrafanaAlertItem> alerts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrafanaEvalMatch(
            String metric,
            Double value,
            Map<String, String> tags
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrafanaAlertItem(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt,
            String endsAt,
            String valueString,
            String fingerprint
    ) {
    }
}
