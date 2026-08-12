package com.roottrace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.audit.AuditService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookEventServiceTest {

    private WebhookEventRepository webhookEventRepository;
    private IncidentRepository incidentRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private ObjectMapper objectMapper;
    private WebhookEventService webhookEventService;

    private User testUser;

    @BeforeEach
    void setUp() {
        webhookEventRepository = mock(WebhookEventRepository.class);
        incidentRepository = mock(IncidentRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();

        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(UUID.randomUUID());
        when(testUser.getEmail()).thenReturn("admin@roottrace.com");
        when(testUser.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        webhookEventService = new WebhookEventService(
                webhookEventRepository,
                incidentRepository,
                userRepository,
                auditService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should detect duplicate webhook and return isDuplicate = true without creating new incident")
    void testProcessWebhookAlert_DuplicateIdempotency() {
        String provider = "PROMETHEUS";
        String eventId = "fp-duplicate-123";

        WebhookEvent existingEvent = new WebhookEvent(provider, eventId, "firing", "{}");
        Incident existingIncident = mock(Incident.class);
        when(existingIncident.getId()).thenReturn(UUID.randomUUID());
        existingEvent.setIncident(existingIncident);

        when(webhookEventRepository.findByProviderAndExternalEventId(provider, eventId))
                .thenReturn(Optional.of(existingEvent));

        ExternalIncidentSource.NormalizedAlert alert = new ExternalIncidentSource.NormalizedAlert(
                eventId, "Alert Title", "Description", "payment-service", "prod",
                IncidentSeverity.CRITICAL, "firing", Map.of(), Map.of()
        );

        WebhookIngestionResponse response = webhookEventService.processWebhookAlert(provider, alert, Map.of());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DUPLICATE");
        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.incidentId()).isEqualTo(existingIncident.getId());
    }

    @Test
    @DisplayName("Should create new incident when alert is received for the first time")
    void testProcessWebhookAlert_NewIncident() {
        String provider = "PROMETHEUS";
        String eventId = "fp-new-456";

        when(webhookEventRepository.findByProviderAndExternalEventId(provider, eventId))
                .thenReturn(Optional.empty());
        when(incidentRepository.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            try {
                var idField = Incident.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(inc, UUID.randomUUID());
            } catch (Exception e) {}
            return inc;
        });

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent ev = inv.getArgument(0);
            ev.setId(UUID.randomUUID());
            return ev;
        });

        ExternalIncidentSource.NormalizedAlert alert = new ExternalIncidentSource.NormalizedAlert(
                eventId, "High CPU Usage: payment-service", "CPU utilization > 95%", "payment-service", "production",
                IncidentSeverity.HIGH, "firing", Map.of(), Map.of()
        );

        WebhookIngestionResponse response = webhookEventService.processWebhookAlert(provider, alert, Map.of("key", "val"));

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.isDuplicate()).isFalse();

        verify(incidentRepository).save(any(Incident.class));
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }
}
