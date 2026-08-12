package com.roottrace.integration;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.integration.dto.GrafanaAlertPayload;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrafanaWebhookServiceTest {

    private WebhookEventService webhookEventService;
    private GrafanaWebhookService grafanaWebhookService;

    @BeforeEach
    void setUp() {
        webhookEventService = mock(WebhookEventService.class);
        grafanaWebhookService = new GrafanaWebhookService(webhookEventService);
    }

    @Test
    @DisplayName("Should normalize Grafana alert payload and map high severity")
    void testProcessGrafanaWebhook_Success() {
        GrafanaAlertPayload.GrafanaAlertItem alertItem = new GrafanaAlertPayload.GrafanaAlertItem(
                "alerting",
                Map.of("service", "auth-service", "severity", "high"),
                Map.of("description", "Auth token validation latency > 2s"),
                "2026-08-12T22:00:00Z",
                null,
                "2.45s",
                "gf-789012"
        );

        GrafanaAlertPayload payload = new GrafanaAlertPayload(
                "Auth Service High Latency",
                "alerting",
                "Latency breach on auth-service",
                "rule-auth-01",
                "Auth Latency Alert",
                "http://grafana:3000/alerts/1",
                "1", "dash-1", "panel-1",
                Map.of("service", "auth-service", "severity", "high"),
                List.of(),
                List.of(alertItem)
        );

        UUID incId = UUID.randomUUID();
        when(webhookEventService.processWebhookAlert(eq("GRAFANA"), any(), any()))
                .thenReturn(new WebhookIngestionResponse("CREATED", "Created incident", incId, "gf-789012", false));

        WebhookIngestionResponse response = grafanaWebhookService.processGrafanaWebhook(payload);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CREATED");

        ArgumentCaptor<ExternalIncidentSource.NormalizedAlert> captor =
                ArgumentCaptor.forClass(ExternalIncidentSource.NormalizedAlert.class);
        verify(webhookEventService).processWebhookAlert(eq("GRAFANA"), captor.capture(), eq(payload));

        ExternalIncidentSource.NormalizedAlert normalized = captor.getValue();
        assertThat(normalized.externalEventId()).isEqualTo("gf-789012");
        assertThat(normalized.service()).isEqualTo("auth-service");
        assertThat(normalized.severity()).isEqualTo(IncidentSeverity.HIGH);
    }
}
