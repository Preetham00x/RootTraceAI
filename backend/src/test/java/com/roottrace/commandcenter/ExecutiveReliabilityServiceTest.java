package com.roottrace.commandcenter;

import com.roottrace.commandcenter.dto.CommandCenterOverviewResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorAiResponse;
import com.roottrace.commandcenter.dto.ExecutiveReliabilityAdvisorResponse;
import com.roottrace.commandcenter.dto.ReliabilityPenaltyResponse;
import com.roottrace.commandcenter.dto.ReliabilityScoreResponse;
import com.roottrace.commandcenter.dto.ServiceHealthSummaryResponse;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutiveReliabilityServiceTest {

    private CommandCenterService commandCenterService;
    private ServiceHealthService serviceHealthService;
    private ReliabilityScoreService reliabilityScoreService;
    private ExecutiveReliabilityPromptBuilder promptBuilder;
    private GeminiExecutiveReliabilityService geminiExecutiveService;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private ExecutiveReliabilityService executiveReliabilityService;

    @BeforeEach
    void setUp() {
        commandCenterService = mock(CommandCenterService.class);
        serviceHealthService = mock(ServiceHealthService.class);
        reliabilityScoreService = mock(ReliabilityScoreService.class);
        promptBuilder = new ExecutiveReliabilityPromptBuilder();
        geminiExecutiveService = mock(GeminiExecutiveReliabilityService.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        User adminUser = new User("admin@roottrace.com", "h", "A", "U", Role.ADMIN);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(adminUser, UUID.randomUUID());
        } catch (Exception e) {}
        when(currentUserService.getCurrentUser()).thenReturn(adminUser);

        executiveReliabilityService = new ExecutiveReliabilityService(
                commandCenterService,
                serviceHealthService,
                reliabilityScoreService,
                promptBuilder,
                geminiExecutiveService,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should generate executive AI advisor briefing and record audit event")
    void testGenerateExecutiveAdvisor_Success() {
        CommandCenterOverviewResponse overview = new CommandCenterOverviewResponse(
                30, 85.0, "LOW", 5, 1, 10, 1, 9, 1, 3, 25.0, 5.0,
                10, 8, 1, 1, 45.0, 4, 1, 0,
                List.of(), List.of(), List.of(), List.of()
        );
        when(commandCenterService.getOverview(30)).thenReturn(overview);
        when(serviceHealthService.getServiceHealthSummaries(30, 5, "risk")).thenReturn(List.of());

        ReliabilityScoreResponse scoreResp = new ReliabilityScoreResponse(
                85.0, 100.0, "LOW", List.of(new ReliabilityPenaltyResponse("SLO_BREACHES", 10.0, "1 SLO breach"))
        );
        when(reliabilityScoreService.calculateReliabilityScore(anyInt(), anyInt(), anyDouble(), anyInt(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(scoreResp);

        ExecutiveReliabilityAdvisorAiResponse aiResp = new ExecutiveReliabilityAdvisorAiResponse(
                "System is broadly reliable with 1 service requiring attention.",
                List.of("1 active SLO breach on checkout API"),
                List.of(new ExecutiveReliabilityAdvisorAiResponse.ServiceAttentionItem("payment-service", "SLO breach", "HIGH")),
                List.of(new ExecutiveReliabilityAdvisorAiResponse.ExecutiveActionItem("Throttle deployments", "Restore budget", "CRITICAL")),
                List.of("90% of services are operating within normal SLO parameters")
        );
        when(geminiExecutiveService.generateExecutiveAdvice(anyString())).thenReturn(aiResp);

        ExecutiveReliabilityAdvisorResponse response = executiveReliabilityService.generateExecutiveAdvisor(30);

        assertThat(response).isNotNull();
        assertThat(response.executiveSummary()).contains("System is broadly reliable");
        assertThat(response.servicesRequiringAttention()).hasSize(1);
        assertThat(response.recommendedActions()).hasSize(1);
        assertThat(response.overallReliabilityScore()).isEqualTo(85.0);

        verify(auditService).record(
                eq(AuditEventType.AI_EXECUTIVE_RELIABILITY_ADVISOR_GENERATED),
                eq("ExecutiveReliability"),
                eq("Organization"),
                eq("admin@roottrace.com"),
                any()
        );
    }
}
