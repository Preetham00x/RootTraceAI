package com.roottrace.integration;

import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.integration.dto.SlackCommandRequest;
import com.roottrace.integration.dto.SlackResponse;
import com.roottrace.intelligence.IncidentBriefingService;
import com.roottrace.intelligence.ServiceRiskService;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;
import com.roottrace.intelligence.dto.ServiceRiskResponse;
import com.roottrace.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlackServiceTest {

    private IncidentRepository incidentRepository;
    private IncidentBriefingService incidentBriefingService;
    private ServiceRiskService serviceRiskService;
    private UserRepository userRepository;
    private SlackService slackService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        incidentBriefingService = mock(IncidentBriefingService.class);
        serviceRiskService = mock(ServiceRiskService.class);
        userRepository = mock(UserRepository.class);

        slackService = new SlackService(
                incidentRepository,
                incidentBriefingService,
                serviceRiskService,
                userRepository
        );
    }

    @Test
    @DisplayName("Should handle Slack '/incident briefing <id>' command")
    void testHandleSlackCommand_Briefing() {
        UUID incId = UUID.randomUUID();
        Incident incident = mock(Incident.class);
        when(incident.getId()).thenReturn(incId);
        when(incident.getTitle()).thenReturn("Payment Timeout");
        when(incident.getService()).thenReturn("payment-service");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.CRITICAL);
        when(incident.getStatus()).thenReturn(IncidentStatus.INVESTIGATING);

        when(incidentRepository.findByIdAndNotDeleted(incId)).thenReturn(Optional.of(incident));

        IncidentBriefingResponse briefing = new IncidentBriefingResponse(
                incId, "Connection pool exhausted", true, 3,
                List.of("Scale workers", "Check DB metrics"), List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now()
        );
        when(incidentBriefingService.generateBriefing(incId)).thenReturn(briefing);

        ServiceRiskResponse risk = new ServiceRiskResponse(
                "payment-service", 78.0, "HIGH", 5, 2, 2, 1, 0.40, 35.0, 2, List.of(), Instant.now()
        );
        when(serviceRiskService.evaluateServiceRisk("payment-service")).thenReturn(risk);

        SlackCommandRequest request = new SlackCommandRequest(
                "/incident", "briefing " + incId, "U12345", "alice", "C67890", "ops-war-room", "http://slack.com"
        );

        SlackResponse response = slackService.handleSlackCommand(request);

        assertThat(response).isNotNull();
        assertThat(response.responseType()).isEqualTo("in_channel");
        assertThat(response.text()).contains("Incident Briefing: Payment Timeout");
        assertThat(response.text()).contains("Risk Tier:* `HIGH`");
        assertThat(response.text()).contains("Recurring:* YES");
    }

    @Test
    @DisplayName("Should handle Slack '/incident create <title>' command")
    void testHandleSlackCommand_Create() {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            return inc;
        });

        SlackCommandRequest request = new SlackCommandRequest(
                "/incident", "create Stripe Webhook 500 Spike", "U12345", "alice", "C67890", "ops-war-room", "http://slack.com"
        );

        SlackResponse response = slackService.handleSlackCommand(request);

        assertThat(response).isNotNull();
        assertThat(response.responseType()).isEqualTo("in_channel");
        assertThat(response.text()).contains("Created new incident");
        assertThat(response.text()).contains("Stripe Webhook 500 Spike");
    }

    @Test
    @DisplayName("Should handle Slack '/incident status <id>' command")
    void testHandleSlackCommand_Status() {
        UUID incId = UUID.randomUUID();
        Incident incident = mock(Incident.class);
        when(incident.getId()).thenReturn(incId);
        when(incident.getTitle()).thenReturn("Database Connection Leak");
        when(incident.getService()).thenReturn("auth-service");
        when(incident.getSeverity()).thenReturn(IncidentSeverity.HIGH);
        when(incident.getStatus()).thenReturn(IncidentStatus.RESOLVED);
        when(incident.getCreatedAt()).thenReturn(Instant.now().minusSeconds(3600));
        when(incident.getResolution()).thenReturn("Increased max connection pool size to 50");

        when(incidentRepository.findByIdAndNotDeleted(incId)).thenReturn(Optional.of(incident));

        SlackCommandRequest request = new SlackCommandRequest(
                "/incident", "status " + incId, "U12345", "alice", "C67890", "ops-war-room", "http://slack.com"
        );

        SlackResponse response = slackService.handleSlackCommand(request);

        assertThat(response).isNotNull();
        assertThat(response.responseType()).isEqualTo("in_channel");
        assertThat(response.text()).contains("Incident Status Report");
        assertThat(response.text()).contains("Increased max connection pool size to 50");
    }

    @Test
    @DisplayName("Should return ephemeral error message on invalid command")
    void testHandleSlackCommand_InvalidCommand() {
        SlackCommandRequest request = new SlackCommandRequest(
                "/incident", "foobar", "U12345", "alice", "C67890", "ops-war-room", "http://slack.com"
        );

        SlackResponse response = slackService.handleSlackCommand(request);

        assertThat(response).isNotNull();
        assertThat(response.responseType()).isEqualTo("ephemeral");
        assertThat(response.text()).contains("Unknown command action");
    }
}
