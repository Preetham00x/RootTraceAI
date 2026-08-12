package com.roottrace.integration;

import com.roottrace.incident.IncidentSeverity;
import com.roottrace.integration.dto.PrometheusAlertPayload;
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

class PrometheusWebhookServiceTest {

    private WebhookEventService webhookEventService;
    private PrometheusWebhookService prometheusWebhookService;

    @BeforeEach
    void setUp() {
        webhookEventService = mock(WebhookEventService.class);
        prometheusWebhookService = new PrometheusWebhookService(webhookEventService);
    }

    @Test
    @DisplayName("Should normalize Prometheus Alertmanager payload and map critical severity safely")
    void testProcessPrometheusWebhook_Success() {
        PrometheusAlertPayload.PrometheusAlertItem alertItem = new PrometheusAlertPayload.PrometheusAlertItem(
                "firing",
                Map.of("alertname", "HighConnectionPoolUsage", "service", "payment-service", "severity", "critical"),
                Map.of("description", "Hikari connection pool usage exceeded 90%"),
                "2026-08-12T22:00:00Z",
                null,
                "http://prometheus:9090",
                "fp-123456"
        );

        PrometheusAlertPayload payload = new PrometheusAlertPayload(
                "4", "group-key-1", "firing", "webhook-receiver",
                Map.of(), Map.of(), Map.of(), "http://alertmanager:9093",
                List.of(alertItem)
        );

        UUID incId = UUID.randomUUID();
        when(webhookEventService.processWebhookAlert(eq("PROMETHEUS"), any(), any()))
                .thenReturn(new WebhookIngestionResponse("CREATED", "Created incident", incId, "fp-123456", false));

        WebhookIngestionResponse response = prometheusWebhookService.processPrometheusWebhook(payload);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.incidentId()).isEqualTo(incId);
        assertThat(response.isDuplicate()).isFalse();

        ArgumentCaptor<ExternalIncidentSource.NormalizedAlert> captor =
                ArgumentCaptor.forClass(ExternalIncidentSource.NormalizedAlert.class);
        verify(webhookEventService).processWebhookAlert(eq("PROMETHEUS"), captor.capture(), eq(payload));

        ExternalIncidentSource.NormalizedAlert normalized = captor.getValue();
        assertThat(normalized.externalEventId()).isEqualTo("fp-123456");
        assertThat(normalized.service()).isEqualTo("payment-service");
        assertThat(normalized.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(normalized.description()).contains("Hikari connection pool");
    }

    @Test
    @DisplayName("Should handle empty payload gracefully")
    void testProcessPrometheusWebhook_EmptyPayload() {
        WebhookIngestionResponse response = prometheusWebhookService.processPrometheusWebhook(null);
        assertThat(response.status()).isEqualTo("IGNORED");
    }
}
