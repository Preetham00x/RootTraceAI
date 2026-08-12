package com.roottrace.integration;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.integration.dto.PrometheusAlertPayload;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PrometheusWebhookService implements ExternalIncidentSource {

    private final WebhookEventService webhookEventService;

    public PrometheusWebhookService(WebhookEventService webhookEventService) {
        this.webhookEventService = webhookEventService;
    }

    @Override
    public String getProvider() {
        return "PROMETHEUS";
    }

    public WebhookIngestionResponse processPrometheusWebhook(PrometheusAlertPayload payload) {
        if (payload == null) {
            return new WebhookIngestionResponse("IGNORED", "Empty Prometheus payload", null, null, false);
        }

        // Extract primary alert or common alert metadata
        String alertname = "PrometheusAlert";
        String description = "Prometheus Alert received";
        String service = "unknown-service";
        String environment = "production";
        String severityStr = "warning";
        String status = (payload.status() != null) ? payload.status() : "firing";
        String eventId = payload.groupKey();

        Map<String, String> labels = new HashMap<>();
        Map<String, String> annotations = new HashMap<>();

        if (payload.commonLabels() != null) {
            labels.putAll(payload.commonLabels());
        }
        if (payload.commonAnnotations() != null) {
            annotations.putAll(payload.commonAnnotations());
        }

        if (payload.alerts() != null && !payload.alerts().isEmpty()) {
            var firstAlert = payload.alerts().get(0);
            if (firstAlert.labels() != null) {
                labels.putAll(firstAlert.labels());
            }
            if (firstAlert.annotations() != null) {
                annotations.putAll(firstAlert.annotations());
            }
            if (firstAlert.fingerprint() != null && !firstAlert.fingerprint().isBlank()) {
                eventId = firstAlert.fingerprint();
            }
        }

        if (labels.containsKey("alertname")) {
            alertname = labels.get("alertname");
        }
        if (labels.containsKey("job")) {
            service = labels.get("job");
        }
        if (labels.containsKey("service")) {
            service = labels.get("service");
        }
        if (labels.containsKey("environment")) {
            environment = labels.get("environment");
        }
        if (labels.containsKey("severity")) {
            severityStr = labels.get("severity");
        }

        if (annotations.containsKey("description")) {
            description = annotations.get("description");
        } else if (annotations.containsKey("summary")) {
            description = annotations.get("summary");
        }

        if (eventId == null || eventId.isBlank()) {
            eventId = alertname + "-" + service + "-" + status;
        }

        IncidentSeverity severity = mapSeverity(severityStr);

        NormalizedAlert normalized = new NormalizedAlert(
                eventId,
                alertname + ": " + service,
                description,
                service,
                environment,
                severity,
                status,
                labels,
                annotations
        );

        return webhookEventService.processWebhookAlert(getProvider(), normalized, payload);
    }

    private IncidentSeverity mapSeverity(String severityStr) {
        if (severityStr == null) return IncidentSeverity.MEDIUM;
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
        return IncidentSeverity.LOW;
    }
}
