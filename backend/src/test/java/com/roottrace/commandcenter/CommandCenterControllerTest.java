package com.roottrace.commandcenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.commandcenter.dto.ActiveIncidentResponse;
import com.roottrace.commandcenter.dto.ActiveIncidentsResponse;
import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorAiResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorResponse;
import com.roottrace.commandcenter.dto.IncidentCommandResponse;
import com.roottrace.commandcenter.dto.ReliabilityEventResponse;
import com.roottrace.commandcenter.dto.ReliabilityEventsResponse;
import com.roottrace.commandcenter.dto.ServiceHealthDetailResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.incident.IncidentSeverity;
import com.roottrace.incident.IncidentStatus;
import com.roottrace.incident.dto.IncidentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommandCenterController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommandCenterControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommandCenterService commandCenterService;

    @MockitoBean
    private ServiceHealthService serviceHealthService;

    @MockitoBean
    private IncidentCommandService incidentCommandService;

    @MockitoBean
    private ActiveIncidentService activeIncidentService;

    @MockitoBean
    private ExecutiveReliabilityService executiveReliabilityService;

    @MockitoBean
    private ReliabilityEventFeedService reliabilityEventFeedService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("Viewer can access command center overview (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetOverview_Viewer_Success() throws Exception {
        CommandCenterOverviewResponse overview = new CommandCenterOverviewResponse(
                30, 92.5, "LOW", 10, 1, 15, 1, 14, 1, 2, 28.0, 5.0,
                15, 13, 1, 1, 35.0, 5, 1, 0,
                List.of(), List.of(), List.of(), List.of()
        );

        when(commandCenterService.getOverview(30)).thenReturn(overview);

        mockMvc.perform(get("/api/command-center/overview?days=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallReliabilityScore").value(92.5))
                .andExpect(jsonPath("$.overallRiskTier").value("LOW"));
    }

    @Test
    @DisplayName("Engineer can access service health summaries (200 OK)")
    @WithMockUser(roles = "ENGINEER")
    void testGetServiceSummaries_Engineer_Success() throws Exception {
        ServiceHealthSummaryResponse summary = new ServiceHealthSummaryResponse(
                "payment-service", 78.0, "MEDIUM", 5, 1, 1, 30.0, 0.2,
                3, 2, 1, 0, 45.0, 2, 0, 0, List.of()
        );

        when(serviceHealthService.getServiceHealthSummaries(30, 50, "risk")).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/command-center/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceName").value("payment-service"))
                .andExpect(jsonPath("$[0].healthScore").value(78.0));
    }

    @Test
    @DisplayName("Admin can access service health detail (200 OK)")
    @WithMockUser(roles = "ADMIN")
    void testGetServiceDetail_Admin_Success() throws Exception {
        ServiceHealthDetailResponse detail = new ServiceHealthDetailResponse(
                "payment-service", 85.0, "LOW",
                new ServiceHealthDetailResponse.ServiceIncidentSummary(5, 1, 4, 1, 1, 25.0, 5.0, 0.2),
                new ServiceHealthDetailResponse.ServiceSloSummary(2, 2, 0, 0, List.of()),
                new ServiceHealthDetailResponse.ServiceErrorBudgetSummary(20.0, 80.0, "HEALTHY"),
                new ServiceHealthDetailResponse.ServiceBurnRateSummary(1.0, "NORMAL", 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(serviceHealthService.getServiceHealthDetail(eq("payment-service"), anyInt())).thenReturn(detail);

        mockMvc.perform(get("/api/command-center/services/payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("payment-service"))
                .andExpect(jsonPath("$.healthScore").value(85.0));
    }

    @Test
    @DisplayName("Viewer can access incident command view (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetIncidentCommand_Viewer_Success() throws Exception {
        UUID incidentId = UUID.randomUUID();
        IncidentResponse inc = new IncidentResponse(
                incidentId, "Outage", "Desc", "payment-service", IncidentSeverity.CRITICAL, IncidentStatus.OPEN,
                "prod", null, Instant.now(), Instant.now(), null, null
        );

        IncidentCommandResponse response = new IncidentCommandResponse(
                inc, null, null, List.of(), null, List.of(),
                new IncidentCommandResponse.SloImpactSummary(true, 0, List.of()),
                new IncidentCommandResponse.ErrorBudgetImpactSummary(30.0, 70.0, "HEALTHY"),
                List.of(), List.of(), List.of("Created"), List.of()
        );

        when(incidentCommandService.getIncidentCommandDetails(incidentId)).thenReturn(response);

        mockMvc.perform(get("/api/command-center/incidents/" + incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.title").value("Outage"));
    }

    @Test
    @DisplayName("Viewer can access active incidents (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetActiveIncidents_Viewer_Success() throws Exception {
        ActiveIncidentResponse activeInc = new ActiveIncidentResponse(
                UUID.randomUUID(), "Crash", "payment-service", IncidentSeverity.CRITICAL,
                IncidentStatus.INVESTIGATING, 45, true, "CRITICAL", "HIGH", 92.0, "IMMEDIATE", Instant.now()
        );

        when(activeIncidentService.getActiveIncidents(any(), any(), anyInt()))
                .thenReturn(new ActiveIncidentsResponse(1, List.of(activeInc)));

        mockMvc.perform(get("/api/command-center/incidents/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActive").value(1))
                .andExpect(jsonPath("$.incidents[0].recommendedAttention").value("IMMEDIATE"));
    }

    @Test
    @DisplayName("Viewer can access executive AI advisor (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetExecutiveAdvisor_Viewer_Success() throws Exception {
        ExecutiveReliabilityAdvisorResponse response = new ExecutiveReliabilityAdvisorResponse(
                "Executive Summary", List.of("Key concern 1"),
                List.of(new ExecutiveReliabilityAdvisorAiResponse.ServiceAttentionItem("payment-service", "SLO breach", "HIGH")),
                List.of(new ExecutiveReliabilityAdvisorAiResponse.ExecutiveActionItem("Scale DB", "Reduce latency", "CRITICAL")),
                List.of("Signal 1"), 88.0, "LOW", 0, Instant.now()
        );

        when(executiveReliabilityService.generateExecutiveAdvisor(30)).thenReturn(response);

        mockMvc.perform(get("/api/command-center/advisor?days=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executiveSummary").value("Executive Summary"))
                .andExpect(jsonPath("$.overallReliabilityScore").value(88.0));
    }

    @Test
    @DisplayName("Viewer can access reliability event feed (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetEventFeed_Viewer_Success() throws Exception {
        ReliabilityEventResponse event = new ReliabilityEventResponse(
                Instant.now(), "SLO_BREACH", "CRITICAL", "payment-service", "slo-1", "SLO breached"
        );

        when(reliabilityEventFeedService.getEventFeed(any(), anyInt(), anyInt()))
                .thenReturn(new ReliabilityEventsResponse(1, List.of(event)));

        mockMvc.perform(get("/api/command-center/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.events[0].type").value("SLO_BREACH"));
    }
}
