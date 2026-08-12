package com.roottrace.integration;

import com.roottrace.integration.dto.GrafanaAlertPayload;
import com.roottrace.integration.dto.PrometheusAlertPayload;
import com.roottrace.integration.dto.SlackCommandRequest;
import com.roottrace.integration.dto.SlackResponse;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
@Tag(name = "External Webhooks", description = "Ingestion endpoints for Prometheus, Grafana, and Slack webhooks")
public class WebhookController {

    private final PrometheusWebhookService prometheusWebhookService;
    private final GrafanaWebhookService grafanaWebhookService;
    private final SlackService slackService;

    public WebhookController(
            PrometheusWebhookService prometheusWebhookService,
            GrafanaWebhookService grafanaWebhookService,
            SlackService slackService) {
        this.prometheusWebhookService = prometheusWebhookService;
        this.grafanaWebhookService = grafanaWebhookService;
        this.slackService = slackService;
    }

    @PostMapping("/prometheus/webhook")
    @Operation(summary = "Ingest alert webhook from Prometheus Alertmanager")
    public ResponseEntity<WebhookIngestionResponse> ingestPrometheusAlert(
            @RequestBody PrometheusAlertPayload payload) {
        return ResponseEntity.ok(prometheusWebhookService.processPrometheusWebhook(payload));
    }

    @PostMapping("/grafana/webhook")
    @Operation(summary = "Ingest alert webhook from Grafana Alerting")
    public ResponseEntity<WebhookIngestionResponse> ingestGrafanaAlert(
            @RequestBody GrafanaAlertPayload payload) {
        return ResponseEntity.ok(grafanaWebhookService.processGrafanaWebhook(payload));
    }

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Handle incoming Slack slash commands")
    public ResponseEntity<SlackResponse> handleSlackCommand(
            @RequestBody SlackCommandRequest request) {
        return ResponseEntity.ok(slackService.handleSlackCommand(request));
    }
}
