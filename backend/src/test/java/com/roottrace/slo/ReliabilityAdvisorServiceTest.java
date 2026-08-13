package com.roottrace.slo;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.postmortem.PostmortemActionItemRepository;
import com.roottrace.slo.dto.ReliabilityAdvisorAiResponse;
import com.roottrace.slo.dto.ReliabilityAdvisorResponse;
import com.roottrace.slo.dto.ReliabilityDashboardResponse;
import com.roottrace.slo.dto.ReliabilityRiskResponse;
import com.roottrace.slo.dto.SloEvaluationResponse;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliabilityAdvisorServiceTest {

    private SloService sloService;
    private ReliabilityRiskService reliabilityRiskService;
    private ReliabilityPromptBuilder promptBuilder;
    private GeminiReliabilityAdvisorService geminiAdvisorService;
    private IncidentRepository incidentRepository;
    private PostmortemActionItemRepository actionItemRepository;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private ReliabilityAdvisorService advisorService;

    @BeforeEach
    void setUp() {
        sloService = mock(SloService.class);
        reliabilityRiskService = mock(ReliabilityRiskService.class);
        promptBuilder = new ReliabilityPromptBuilder();
        geminiAdvisorService = mock(GeminiReliabilityAdvisorService.class);
        incidentRepository = mock(IncidentRepository.class);
        actionItemRepository = mock(PostmortemActionItemRepository.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        User user = new User("admin@roottrace.com", "h", "A", "U", Role.ADMIN);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, UUID.randomUUID());
        } catch (Exception e) {}
        when(currentUserService.getCurrentUser()).thenReturn(user);

        advisorService = new ReliabilityAdvisorService(
                sloService,
                reliabilityRiskService,
                promptBuilder,
                geminiAdvisorService,
                incidentRepository,
                actionItemRepository,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should generate reliability advisory report using Gemini synthesis and audit event")
    void testGenerateReliabilityAdvice_Success() {
        ReliabilityDashboardResponse dashboard = new ReliabilityDashboardResponse(
                "payment-service", 75.0, "HIGH", List.of(), 1, 80.0, 3.5, 4, 0.25, 2, Instant.now()
        );
        when(sloService.getReliabilityDashboard("payment-service")).thenReturn(dashboard);

        ReliabilityRiskResponse risk = new ReliabilityRiskResponse(
                "payment-service", 75.0, "HIGH", 1, 80.0, 0, 1, 0.25, 2,
                List.of("1 active breach (+25)"), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("payment-service")).thenReturn(risk);
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        ReliabilityAdvisorAiResponse aiResponse = new ReliabilityAdvisorAiResponse(
                "Payment service is experiencing elevated error budget consumption.",
                List.of("High latency on checkout queries"),
                List.of("Scale redis connection pool", "Enforce circuit breaking"),
                "HIGH"
        );
        when(geminiAdvisorService.generateAdvisorRecommendations(anyString())).thenReturn(aiResponse);

        ReliabilityAdvisorResponse response = advisorService.generateReliabilityAdvice("payment-service");

        assertThat(response).isNotNull();
        assertThat(response.serviceName()).isEqualTo("payment-service");
        assertThat(response.executiveSummary()).contains("Payment service is experiencing");
        assertThat(response.recommendedActions()).hasSize(2);
        assertThat(response.priority()).isEqualTo("HIGH");

        verify(auditService).record(
                eq(AuditEventType.RELIABILITY_ADVISOR_GENERATED),
                eq("ServiceReliability"),
                eq("payment-service"),
                eq("admin@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should generate reliability advice when service has active SLO evaluations")
    void testGenerateReliabilityAdvice_WithActiveSlos() {
        SloEvaluationResponse sloEval = new SloEvaluationResponse(
                UUID.randomUUID(), "payment-service", "Payment Latency", 99.0, 98.2, -0.8,
                SloStatus.WARNING, 5000L, 4910L, 90L, 10.0, 90.0, Instant.now()
        );

        ReliabilityDashboardResponse dashboard = new ReliabilityDashboardResponse(
                "payment-service", 60.0, "HIGH", List.of(sloEval), 0, 90.0, 2.0, 1, 0.0, 0, Instant.now()
        );
        when(sloService.getReliabilityDashboard("payment-service")).thenReturn(dashboard);

        ReliabilityRiskResponse risk = new ReliabilityRiskResponse(
                "payment-service", 60.0, "HIGH", 0, 90.0, 0, 0, 0.0, 0,
                List.of("90% error budget consumed"), Instant.now()
        );
        when(reliabilityRiskService.evaluateReliabilityRisk("payment-service")).thenReturn(risk);
        when(incidentRepository.findAllNotDeleted(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(actionItemRepository.findAll()).thenReturn(List.of());

        when(geminiAdvisorService.generateAdvisorRecommendations(anyString()))
                .thenReturn(new ReliabilityAdvisorAiResponse("Latency concerns", List.of(), List.of("Optimize DB index"), "MEDIUM"));

        ReliabilityAdvisorResponse response = advisorService.generateReliabilityAdvice("payment-service");

        assertThat(response).isNotNull();
        assertThat(response.recommendedActions()).contains("Optimize DB index");
    }
}
