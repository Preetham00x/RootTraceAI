package com.roottrace.integration;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.integration.dto.GrafanaAlertPayload;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class GrafanaWebhookService implements ExternalIncidentSource {

    private final WebhookEventService webhookEventService;

    public GrafanaWebhookService(WebhookEventService webhookEventService) {
        this.webhookEventService = webhookEventService;
    }

    @Override
    public String getProvider() {
        return "GRAFANA";
    }

    public WebhookIngestionResponse processGrafanaWebhook(GrafanaAlertPayload payload) {
        if (payload == null) {
            return new WebhookIngestionResponse("IGNORED", "Empty Grafana payload", null, null, false);
        }

        String title = (payload.title() != null && !payload.title().isBlank())
                ? payload.title()
                : (payload.ruleName() != null ? payload.ruleName() : "Grafana Alert");

        String description = (payload.message() != null && !payload.message().isBlank())
                ? payload.message()
                : "Grafana alert notification for rule: " + title;

        String service = "unknown-service";
        String environment = "production";
        String severityStr = "warning";
        String eventId = (payload.ruleId() != null) ? payload.ruleId() : (title + "-" + payload.state());

        Map<String, String> labels = new HashMap<>();
        Map<String, String> annotations = new HashMap<>();

        if (payload.tags() != null) {
            labels.putAll(payload.tags());
        }

        if (payload.alerts() != null && !payload.alerts().isEmpty()) {
            var first = payload.alerts().get(0);
            if (first.labels() != null) {
                labels.putAll(first.labels());
            }
            if (first.annotations() != null) {
                annotations.putAll(first.annotations());
            }
            if (first.fingerprint() != null && !first.fingerprint().isBlank()) {
                eventId = first.fingerprint();
            }
        }

        if (labels.containsKey("service")) {
            service = labels.get("service");
        } else if (labels.containsKey("app")) {
            service = labels.get("app");
        } else if (labels.containsKey("job")) {
            service = labels.get("job");
        }

        if (labels.containsKey("environment")) {
            environment = labels.get("environment");
        } else if (labels.containsKey("env")) {
            environment = labels.get("env");
        }

        if (labels.containsKey("severity")) {
            severityStr = labels.get("severity");
        }

        IncidentSeverity severity = mapSeverity(severityStr, payload.state());

        NormalizedAlert normalized = new NormalizedAlert(
                eventId,
                title + ": " + service,
                description,
                service,
                environment,
                severity,
                payload.state() != null ? payload.state() : "alerting",
                labels,
                annotations
        );

        return webhookEventService.processWebhookAlert(getProvider(), normalized, payload);
    }

    private IncidentSeverity mapSeverity(String severityStr, String state) {
        if (severityStr != null) {
            String s = severityStr.toLowerCase(Locale.ROOT).trim();
            if (s.contains("crit") || s.contains("fatal") || s.contains("p1") || s.contains("sev1")) {
                return IncidentSeverity.CRITICAL;
            }
            if (s.contains("high") || s.contains("err") || s.contains("p2") || s.contains("sev2")) {
                return IncidentSeverity.HIGH;
            }
            if (s.contains("warn") || s.contains("med") || s.contains("p3") || s.contains("sev3")) {
                return IncidentSeverity.MEDIUM;
            }
            if (s.contains("info") || s.contains("low")) {
                return IncidentSeverity.LOW;
            }
        }

        if ("alerting".equalsIgnoreCase(state)) {
            return IncidentSeverity.HIGH;
        }

        return IncidentSeverity.MEDIUM;
    }
}
