package com.roottrace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roottrace.common.exception.GlobalExceptionHandler;
import com.roottrace.common.security.JwtAuthenticationFilter;
import com.roottrace.common.security.JwtService;
import com.roottrace.integration.dto.CreateJiraTicketRequest;
import com.roottrace.integration.dto.GrafanaAlertPayload;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.integration.dto.KubernetesPodStatus;
import com.roottrace.integration.dto.PrometheusAlertPayload;
import com.roottrace.integration.dto.RunbookExecutionRequest;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.integration.dto.SlackCommandRequest;
import com.roottrace.integration.dto.SlackResponse;
import com.roottrace.integration.dto.WebhookIngestionResponse;
import com.roottrace.user.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {IntegrationController.class, WebhookController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IntegrationControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrometheusWebhookService prometheusWebhookService;

    @MockitoBean
    private GrafanaWebhookService grafanaWebhookService;

    @MockitoBean
    private SlackService slackService;

    @MockitoBean
    private JiraService jiraService;

    @MockitoBean
    private RunbookExecutionService runbookExecutionService;

    @MockitoBean
    private KubernetesService kubernetesService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID actionItemId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();

    @Test
    @DisplayName("Should ingest Prometheus alert webhook anonymously (200 OK)")
    void testIngestPrometheusWebhook() throws Exception {
        PrometheusAlertPayload payload = new PrometheusAlertPayload(
                "4", "g1", "firing", "r1", Map.of(), Map.of(), Map.of(), "http://prom", List.of()
        );

        when(prometheusWebhookService.processPrometheusWebhook(any()))
                .thenReturn(new WebhookIngestionResponse("CREATED", "Created incident", incidentId, "fp-1", false));

        mockMvc.perform(post("/api/integrations/prometheus/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.incidentId").value(incidentId.toString()));
    }

    @Test
    @DisplayName("Should ingest Grafana alert webhook anonymously (200 OK)")
    void testIngestGrafanaWebhook() throws Exception {
        GrafanaAlertPayload payload = new GrafanaAlertPayload(
                "Alert", "alerting", "Message", "r1", "rule", "http://grafana", "1", "d1", "p1", Map.of(), List.of(), List.of()
        );

        when(grafanaWebhookService.processGrafanaWebhook(any()))
                .thenReturn(new WebhookIngestionResponse("CREATED", "Created incident", incidentId, "gf-1", false));

        mockMvc.perform(post("/api/integrations/grafana/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("Should handle Slack command webhook (200 OK)")
    void testHandleSlackCommand() throws Exception {
        SlackCommandRequest request = new SlackCommandRequest("/incident", "status " + incidentId, "U1", "alice", "C1", "general", "http://slack");
        when(slackService.handleSlackCommand(any())).thenReturn(SlackResponse.inChannel("Status report"));

        mockMvc.perform(post("/api/integrations/slack/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_type").value("in_channel"))
                .andExpect(jsonPath("$.text").value("Status report"));
    }

    @Test
    @DisplayName("Engineer can create Jira ticket for postmortem action item (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testCreateJiraTicket_Engineer_Success() throws Exception {
        CreateJiraTicketRequest request = new CreateJiraTicketRequest("SRE", "Task", "Summary", "Description");
        JiraTicketResponse response = new JiraTicketResponse(
                UUID.randomUUID(), incidentId, actionItemId, "JIRA", "SRE-101", "http://jira/SRE-101", "CREATED"
        );

        when(jiraService.createJiraTicket(eq(incidentId), eq(actionItemId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/action-items/{actionItemId}/jira", incidentId, actionItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalTicketId").value("SRE-101"))
                .andExpect(jsonPath("$.provider").value("JIRA"));
    }

    @Test
    @DisplayName("Viewer cannot create Jira ticket (403 Forbidden)")
    @WithMockUser(roles = "VIEWER")
    void testCreateJiraTicket_Viewer_Forbidden() throws Exception {
        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem/action-items/{actionItemId}/jira", incidentId, actionItemId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer can request runbook execution (201 Created)")
    @WithMockUser(roles = "ENGINEER")
    void testRequestRunbookExecution_Engineer_Success() throws Exception {
        RunbookExecutionRequest request = new RunbookExecutionRequest("kubectl get pods");
        UserDto userDto = new UserDto(UUID.randomUUID(), "engineer@roottrace.com", "Alice", "Smith", "ENGINEER");

        RunbookExecutionResponse response = new RunbookExecutionResponse(
                UUID.randomUUID(), incidentId, stepId, "kubectl get pods", RunbookExecutionStatus.REQUESTED,
                userDto, null, null, null, null, null, Instant.now(), Instant.now()
        );

        when(runbookExecutionService.requestExecution(eq(incidentId), eq(stepId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/{incidentId}/runbooks/{stepId}/execute", incidentId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executionStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.command").value("kubectl get pods"));
    }

    @Test
    @DisplayName("Admin can approve runbook execution (200 OK)")
    @WithMockUser(roles = "ADMIN")
    void testApproveRunbookExecution_Admin_Success() throws Exception {
        UserDto adminDto = new UserDto(UUID.randomUUID(), "admin@roottrace.com", "Admin", "User", "ADMIN");

        RunbookExecutionResponse response = new RunbookExecutionResponse(
                UUID.randomUUID(), incidentId, stepId, "kubectl get pods", RunbookExecutionStatus.SUCCEEDED,
                adminDto, adminDto, "Pod running", null, Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );

        when(runbookExecutionService.approveAndExecute(eq(incidentId), eq(stepId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/{incidentId}/runbooks/{stepId}/approve", incidentId, stepId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.output").value("Pod running"));
    }

    @Test
    @DisplayName("Engineer cannot approve runbook execution (403 Forbidden)")
    @WithMockUser(roles = "ENGINEER")
    void testApproveRunbookExecution_Engineer_Forbidden() throws Exception {
        mockMvc.perform(post("/api/incidents/{incidentId}/runbooks/{stepId}/approve", incidentId, stepId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Viewer can read Kubernetes pods (200 OK)")
    @WithMockUser(roles = "VIEWER")
    void testGetKubernetesPods_Viewer_Success() throws Exception {
        when(kubernetesService.getPods("production", "payment-service"))
                .thenReturn(List.of(new KubernetesPodStatus.PodInfo(
                        "payment-service-pod", "production", "Running", 0, "node-1", "10.0.0.1", Instant.now(), Map.of()
                )));

        mockMvc.perform(get("/api/integrations/kubernetes/pods?namespace=production&service=payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("payment-service-pod"))
                .andExpect(jsonPath("$[0].phase").value("Running"));
    }
}
