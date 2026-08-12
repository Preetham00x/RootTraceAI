package com.roottrace.investigation;

import com.roottrace.ai.diagnosis.AiDiagnosis;
import com.roottrace.ai.diagnosis.AiDiagnosisRepository;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.investigation.dto.CreateInvestigationPlanRequest;
import com.roottrace.investigation.dto.GenerateInvestigationPlanRequest;
import com.roottrace.investigation.dto.InvestigationPlanAiResponse;
import com.roottrace.investigation.dto.InvestigationPlanResponse;
import com.roottrace.investigation.dto.InvestigationStepResponse;
import com.roottrace.investigation.dto.UpdateInvestigationStepRequest;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import com.roottrace.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationPlanServiceTest {

    private IncidentRepository incidentRepository;
    private AiDiagnosisRepository diagnosisRepository;
    private InvestigationPlanRepository planRepository;
    private InvestigationStepRepository stepRepository;
    private UserRepository userRepository;
    private GeminiInvestigationService geminiInvestigationService;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private InvestigationPlanService planService;

    private User testUser;
    private Incident testIncident;
    private AiDiagnosis testDiagnosis;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        diagnosisRepository = mock(AiDiagnosisRepository.class);
        planRepository = mock(InvestigationPlanRepository.class);
        stepRepository = mock(InvestigationStepRepository.class);
        userRepository = mock(UserRepository.class);
        geminiInvestigationService = mock(GeminiInvestigationService.class);
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(UUID.randomUUID());
        when(testUser.getEmail()).thenReturn("engineer@roottrace.com");
        when(testUser.getFirstName()).thenReturn("Jane");
        when(testUser.getLastName()).thenReturn("Doe");
        when(testUser.getRole()).thenReturn(Role.ENGINEER);

        testIncident = mock(Incident.class);
        when(testIncident.getId()).thenReturn(UUID.randomUUID());
        when(testIncident.getTitle()).thenReturn("Database Connection Outage");
        when(testIncident.isDeleted()).thenReturn(false);

        testDiagnosis = mock(AiDiagnosis.class);
        when(testDiagnosis.getId()).thenReturn(UUID.randomUUID());
        when(testDiagnosis.getIncident()).thenReturn(testIncident);

        when(currentUserService.getCurrentUser()).thenReturn(testUser);

        planService = new InvestigationPlanService(
                incidentRepository,
                diagnosisRepository,
                planRepository,
                stepRepository,
                userRepository,
                geminiInvestigationService,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should generate investigation plan from AI diagnosis and persist")
    void testGeneratePlan_Success() {
        UUID incidentId = testIncident.getId();
        UUID diagnosisId = testDiagnosis.getId();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
        when(diagnosisRepository.findById(diagnosisId)).thenReturn(Optional.of(testDiagnosis));

        InvestigationPlanAiResponse aiResponse = new InvestigationPlanAiResponse(
                "Remediation Plan for DB Outage",
                List.of(
                        new InvestigationPlanAiResponse.StepAiResponse("Check Connection Count", "Inspect RDS metrics"),
                        new InvestigationPlanAiResponse.StepAiResponse("Restart Service", "Rolling restart of backend pods")
                )
        );
        when(geminiInvestigationService.generatePlan(testIncident, testDiagnosis)).thenReturn(aiResponse);

        when(planRepository.save(any(InvestigationPlan.class))).thenAnswer(invocation -> {
            InvestigationPlan p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        when(stepRepository.save(any(InvestigationStep.class))).thenAnswer(invocation -> {
            InvestigationStep s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        GenerateInvestigationPlanRequest request = new GenerateInvestigationPlanRequest(diagnosisId);
        InvestigationPlanResponse response = planService.generatePlan(incidentId, request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Remediation Plan for DB Outage");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).title()).isEqualTo("Check Connection Count");
        assertThat(response.steps().get(0).stepOrder()).isEqualTo(1);
        assertThat(response.steps().get(1).stepOrder()).isEqualTo(2);

        verify(auditService).record(
                eq(AuditEventType.INVESTIGATION_PLAN_CREATED),
                eq("InvestigationPlan"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException if diagnosis does not belong to incident")
    void testGeneratePlan_DiagnosisIncidentMismatch() {
        UUID incidentId = testIncident.getId();
        UUID diagnosisId = UUID.randomUUID();

        Incident otherIncident = mock(Incident.class);
        when(otherIncident.getId()).thenReturn(UUID.randomUUID());

        AiDiagnosis mismatchedDiagnosis = mock(AiDiagnosis.class);
        when(mismatchedDiagnosis.getId()).thenReturn(diagnosisId);
        when(mismatchedDiagnosis.getIncident()).thenReturn(otherIncident);

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));
        when(diagnosisRepository.findById(diagnosisId)).thenReturn(Optional.of(mismatchedDiagnosis));

        GenerateInvestigationPlanRequest request = new GenerateInvestigationPlanRequest(diagnosisId);

        assertThatThrownBy(() -> planService.generatePlan(incidentId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Diagnosis does not belong to the specified incident");
    }

    @Test
    @DisplayName("Should create investigation plan manually")
    void testCreatePlan_Manual() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        CreateInvestigationPlanRequest request = new CreateInvestigationPlanRequest(
                "Manual Investigation Runbook",
                null,
                List.of(
                        new CreateInvestigationPlanRequest.CreateInvestigationStepRequest("Triage", "Check error logs", null),
                        new CreateInvestigationPlanRequest.CreateInvestigationStepRequest("Mitigate", "Scale replicas", null)
                )
        );

        when(planRepository.save(any(InvestigationPlan.class))).thenAnswer(invocation -> {
            InvestigationPlan p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        InvestigationPlanResponse response = planService.createPlan(incidentId, request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Manual Investigation Runbook");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).title()).isEqualTo("Triage");

        verify(auditService).record(
                eq(AuditEventType.INVESTIGATION_PLAN_CREATED),
                eq("InvestigationPlan"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should list investigation plans for an incident")
    void testGetPlans() {
        UUID incidentId = testIncident.getId();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        InvestigationPlan plan = new InvestigationPlan(testIncident, testDiagnosis, "Plan A", testUser);
        plan.setId(UUID.randomUUID());
        when(planRepository.findByIncidentIdWithSteps(incidentId)).thenReturn(List.of(plan));

        List<InvestigationPlanResponse> plans = planService.getPlans(incidentId);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).title()).isEqualTo("Plan A");
    }

    @Test
    @DisplayName("Should update step status to COMPLETED and record completedAt timestamp")
    void testUpdateStep_ToCompleted() {
        UUID incidentId = testIncident.getId();
        UUID planId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        InvestigationPlan plan = new InvestigationPlan(testIncident, testDiagnosis, "Plan A", testUser);
        plan.setId(planId);
        InvestigationStep step = new InvestigationStep(plan, 1, "Check Logs", "Inspect pod logs");
        step.setId(stepId);
        step.setStatus(InvestigationStepStatus.IN_PROGRESS);

        when(planRepository.findByIdAndIncidentIdWithSteps(planId, incidentId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId(stepId, planId)).thenReturn(Optional.of(step));
        when(stepRepository.save(any(InvestigationStep.class))).thenAnswer(invocation -> {
            InvestigationStep s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(stepId);
            }
            return s;
        });

        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.COMPLETED,
                "Logs verified - no NullPointerException",
                null
        );

        InvestigationStepResponse response = planService.updateStep(incidentId, planId, stepId, request);

        assertThat(response.status()).isEqualTo(InvestigationStepStatus.COMPLETED);
        assertThat(response.evidence()).isEqualTo("Logs verified - no NullPointerException");
        assertThat(response.completedAt()).isNotNull();

        verify(auditService).record(
                eq(AuditEventType.INVESTIGATION_STEP_COMPLETED),
                eq("InvestigationStep"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should clear completedAt when transitioning away from COMPLETED")
    void testUpdateStep_AwayFromCompleted() {
        UUID incidentId = testIncident.getId();
        UUID planId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        InvestigationPlan plan = new InvestigationPlan(testIncident, testDiagnosis, "Plan A", testUser);
        plan.setId(planId);
        InvestigationStep step = new InvestigationStep(plan, 1, "Check Logs", "Inspect pod logs");
        step.setId(stepId);
        step.setStatus(InvestigationStepStatus.COMPLETED);
        step.setCompletedAt(Instant.now());

        when(planRepository.findByIdAndIncidentIdWithSteps(planId, incidentId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId(stepId, planId)).thenReturn(Optional.of(step));
        when(stepRepository.save(any(InvestigationStep.class))).thenAnswer(invocation -> {
            InvestigationStep s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(stepId);
            }
            return s;
        });

        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.IN_PROGRESS,
                "Reopening step for re-validation",
                null
        );

        InvestigationStepResponse response = planService.updateStep(incidentId, planId, stepId, request);

        assertThat(response.status()).isEqualTo(InvestigationStepStatus.IN_PROGRESS);
        assertThat(response.completedAt()).isNull();

        verify(auditService).record(
                eq(AuditEventType.INVESTIGATION_STEP_UPDATED),
                eq("InvestigationStep"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException on invalid status transition")
    void testUpdateStep_InvalidTransition() {
        UUID incidentId = testIncident.getId();
        UUID planId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(testIncident));

        InvestigationPlan plan = new InvestigationPlan(testIncident, testDiagnosis, "Plan A", testUser);
        InvestigationStep step = new InvestigationStep(plan, 1, "Check Logs", "Inspect pod logs");
        step.setStatus(InvestigationStepStatus.COMPLETED);

        when(planRepository.findByIdAndIncidentIdWithSteps(planId, incidentId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId(stepId, planId)).thenReturn(Optional.of(step));

        // COMPLETED -> SKIPPED is invalid
        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.SKIPPED,
                null,
                null
        );

        assertThatThrownBy(() -> planService.updateStep(incidentId, planId, stepId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot transition step status from COMPLETED to SKIPPED");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if incident or plan does not exist")
    void testUpdateStep_NotFound() {
        UUID incidentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.empty());

        UpdateInvestigationStepRequest request = new UpdateInvestigationStepRequest(
                InvestigationStepStatus.IN_PROGRESS,
                null,
                null
        );

        assertThatThrownBy(() -> planService.updateStep(incidentId, planId, stepId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
