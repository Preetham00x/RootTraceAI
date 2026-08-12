package com.roottrace.integration;

import com.roottrace.common.audit.AuditService;
import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.integration.dto.RunbookExecutionRequest;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.investigation.InvestigationStepRepository;
import com.roottrace.user.Role;
import com.roottrace.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

class RunbookExecutionServiceTest {

    private RunbookExecutionRepository runbookExecutionRepository;
    private IncidentRepository incidentRepository;
    private InvestigationStepRepository investigationStepRepository;
    private InfrastructureExecutionService infrastructureExecutionService;
    private CurrentUserService currentUserService;
    private AuditService auditService;
    private RunbookExecutionService runbookExecutionService;

    private Incident incident;
    private InvestigationStep step;
    private User engineerUser;
    private User adminUser;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        runbookExecutionRepository = mock(RunbookExecutionRepository.class);
        incidentRepository = mock(IncidentRepository.class);
        investigationStepRepository = mock(InvestigationStepRepository.class);
        infrastructureExecutionService = new MockInfrastructureExecutionService();
        currentUserService = mock(CurrentUserService.class);
        auditService = mock(AuditService.class);

        engineerUser = mock(User.class);
        when(engineerUser.getId()).thenReturn(UUID.randomUUID());
        when(engineerUser.getEmail()).thenReturn("engineer@roottrace.com");
        when(engineerUser.getRole()).thenReturn(Role.ENGINEER);

        adminUser = mock(User.class);
        when(adminUser.getId()).thenReturn(UUID.randomUUID());
        when(adminUser.getEmail()).thenReturn("admin@roottrace.com");
        when(adminUser.getRole()).thenReturn(Role.ADMIN);

        incident = mock(Incident.class);
        when(incident.getId()).thenReturn(incidentId);
        when(incident.getService()).thenReturn("payment-service");
        when(incidentRepository.findByIdAndNotDeleted(incidentId)).thenReturn(Optional.of(incident));

        step = mock(InvestigationStep.class);
        when(step.getId()).thenReturn(stepId);
        when(step.getTitle()).thenReturn("Inspect active TCP sockets");
        when(investigationStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        runbookExecutionService = new RunbookExecutionService(
                runbookExecutionRepository,
                incidentRepository,
                investigationStepRepository,
                infrastructureExecutionService,
                currentUserService,
                auditService
        );
    }

    @Test
    @DisplayName("Should request runbook execution in REQUESTED state")
    void testRequestExecution_Success() {
        when(currentUserService.getCurrentUser()).thenReturn(engineerUser);

        when(runbookExecutionRepository.save(any(RunbookExecution.class))).thenAnswer(inv -> {
            RunbookExecution e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        RunbookExecutionRequest request = new RunbookExecutionRequest("kubectl get pods -n production");
        RunbookExecutionResponse response = runbookExecutionService.requestExecution(incidentId, stepId, request);

        assertThat(response).isNotNull();
        assertThat(response.command()).isEqualTo("kubectl get pods -n production");
        assertThat(response.executionStatus()).isEqualTo(RunbookExecutionStatus.REQUESTED);

        verify(auditService).record(
                eq(AuditEventType.RUNBOOK_EXECUTION_REQUESTED),
                eq("RunbookExecution"),
                any(),
                eq("engineer@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should reject destructive/unsafe command execution requests")
    void testRequestExecution_RejectUnsafeCommand() {
        when(currentUserService.getCurrentUser()).thenReturn(engineerUser);

        RunbookExecutionRequest request = new RunbookExecutionRequest("rm -rf /var/data && reboot");

        assertThatThrownBy(() -> runbookExecutionService.requestExecution(incidentId, stepId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("contains unsafe or destructive operations");
    }

    @Test
    @DisplayName("Should approve and execute runbook command transition to SUCCEEDED")
    void testApproveAndExecute_Success() {
        when(currentUserService.getCurrentUser()).thenReturn(adminUser);

        RunbookExecution execution = new RunbookExecution(incident, step, "netstat -an | grep 5432", engineerUser);
        execution.setId(UUID.randomUUID());

        when(runbookExecutionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(runbookExecutionRepository.save(any(RunbookExecution.class))).thenAnswer(inv -> inv.getArgument(0));

        RunbookExecutionResponse response = runbookExecutionService.approveAndExecute(incidentId, stepId, execution.getId());

        assertThat(response.executionStatus()).isEqualTo(RunbookExecutionStatus.SUCCEEDED);
        assertThat(response.output()).contains("Active Internet connections");
        assertThat(response.approvedBy().email()).isEqualTo("admin@roottrace.com");

        verify(auditService).record(
                eq(AuditEventType.RUNBOOK_EXECUTION_APPROVED),
                eq("RunbookExecution"),
                eq(execution.getId().toString()),
                eq("admin@roottrace.com"),
                any()
        );
        verify(auditService).record(
                eq(AuditEventType.RUNBOOK_EXECUTION_COMPLETED),
                eq("RunbookExecution"),
                eq(execution.getId().toString()),
                eq("admin@roottrace.com"),
                any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException if approving execution not in REQUESTED status")
    void testApproveAndExecute_InvalidStatus() {
        when(currentUserService.getCurrentUser()).thenReturn(adminUser);

        RunbookExecution execution = new RunbookExecution(incident, step, "curl -I localhost", engineerUser);
        execution.setId(UUID.randomUUID());
        execution.setExecutionStatus(RunbookExecutionStatus.SUCCEEDED);

        when(runbookExecutionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> runbookExecutionService.approveAndExecute(incidentId, stepId, execution.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot approve execution in status");
    }

    @Test
    @DisplayName("Should retrieve historical runbook executions for incident")
    void testGetExecutions_Success() {
        RunbookExecution execution = new RunbookExecution(incident, step, "kubectl get pods", engineerUser);
        execution.setId(UUID.randomUUID());

        when(runbookExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId))
                .thenReturn(List.of(execution));

        List<RunbookExecutionResponse> list = runbookExecutionService.getExecutions(incidentId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).command()).isEqualTo("kubectl get pods");
    }
}
