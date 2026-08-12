package com.roottrace.integration;

import com.roottrace.common.audit.AuditEventType;
import com.roottrace.common.audit.AuditService;
import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.common.security.CurrentUserService;
import com.roottrace.incident.Incident;
import com.roottrace.incident.IncidentRepository;
import com.roottrace.integration.dto.RunbookExecutionRequest;
import com.roottrace.integration.dto.RunbookExecutionResponse;
import com.roottrace.investigation.InvestigationStep;
import com.roottrace.investigation.InvestigationStepRepository;
import com.roottrace.user.User;
import com.roottrace.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RunbookExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RunbookExecutionService.class);

    private final RunbookExecutionRepository runbookExecutionRepository;
    private final IncidentRepository incidentRepository;
    private final InvestigationStepRepository investigationStepRepository;
    private final InfrastructureExecutionService infrastructureExecutionService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public RunbookExecutionService(
            RunbookExecutionRepository runbookExecutionRepository,
            IncidentRepository incidentRepository,
            InvestigationStepRepository investigationStepRepository,
            InfrastructureExecutionService infrastructureExecutionService,
            CurrentUserService currentUserService,
            AuditService auditService) {
        this.runbookExecutionRepository = runbookExecutionRepository;
        this.incidentRepository = incidentRepository;
        this.investigationStepRepository = investigationStepRepository;
        this.infrastructureExecutionService = infrastructureExecutionService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional
    public RunbookExecutionResponse requestExecution(
            UUID incidentId,
            UUID stepId,
            RunbookExecutionRequest request) {

        Incident incident = incidentRepository.findByIdAndNotDeleted(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        InvestigationStep step = null;
        if (stepId != null) {
            step = investigationStepRepository.findById(stepId)
                    .orElseThrow(() -> new ResourceNotFoundException("InvestigationStep", stepId));
        }

        String command = (request != null && request.command() != null && !request.command().isBlank())
                ? request.command().trim()
                : (step != null ? step.getTitle() : "kubectl get pods");

        if (!infrastructureExecutionService.isCommandSafe(command)) {
            throw new BadRequestException("Command execution rejected: contains unsafe or destructive operations.");
        }

        User currentUser = currentUserService.getCurrentUser();

        RunbookExecution execution = new RunbookExecution(
                incident,
                step,
                command,
                currentUser
        );

        RunbookExecution saved = runbookExecutionRepository.save(execution);

        auditService.record(
                AuditEventType.RUNBOOK_EXECUTION_REQUESTED,
                "RunbookExecution",
                saved.getId().toString(),
                currentUser.getEmail(),
                "Requested runbook execution for command: " + command
        );

        return mapToResponse(saved);
    }

    @Transactional
    public RunbookExecutionResponse approveAndExecute(
            UUID incidentId,
            UUID stepId,
            UUID executionId) {

        Incident incident = incidentRepository.findByIdAndNotDeleted(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        RunbookExecution execution;
        if (executionId != null) {
            execution = runbookExecutionRepository.findById(executionId)
                    .orElseThrow(() -> new ResourceNotFoundException("RunbookExecution", executionId));
        } else {
            // Find latest requested execution for step
            List<RunbookExecution> list = runbookExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
            execution = list.stream()
                    .filter(e -> e.getExecutionStatus() == RunbookExecutionStatus.REQUESTED
                            && (stepId == null || (e.getInvestigationStep() != null && e.getInvestigationStep().getId().equals(stepId))))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("No pending runbook execution found to approve for step: " + stepId));
        }

        if (execution.getExecutionStatus() != RunbookExecutionStatus.REQUESTED) {
            throw new BadRequestException("Cannot approve execution in status: " + execution.getExecutionStatus());
        }

        User adminUser = currentUserService.getCurrentUser();
        execution.setApprovedBy(adminUser);
        execution.setExecutionStatus(RunbookExecutionStatus.APPROVED);

        auditService.record(
                AuditEventType.RUNBOOK_EXECUTION_APPROVED,
                "RunbookExecution",
                execution.getId().toString(),
                adminUser.getEmail(),
                "Approved runbook execution: " + execution.getCommand()
        );

        // Execute command
        execution.setExecutionStatus(RunbookExecutionStatus.RUNNING);
        execution.setStartedAt(Instant.now());

        InfrastructureExecutionService.CommandResult result = infrastructureExecutionService.executeCommand(
                execution.getCommand(),
                incident.getService()
        );

        execution.setCompletedAt(Instant.now());
        if (result.successful()) {
            execution.setExecutionStatus(RunbookExecutionStatus.SUCCEEDED);
            execution.setOutput(result.stdout());
            auditService.record(
                    AuditEventType.RUNBOOK_EXECUTION_COMPLETED,
                    "RunbookExecution",
                    execution.getId().toString(),
                    adminUser.getEmail(),
                    "Runbook command succeeded: " + execution.getCommand()
            );
        } else {
            execution.setExecutionStatus(RunbookExecutionStatus.FAILED);
            execution.setErrorOutput(result.stderr());
            auditService.record(
                    AuditEventType.RUNBOOK_EXECUTION_FAILED,
                    "RunbookExecution",
                    execution.getId().toString(),
                    adminUser.getEmail(),
                    "Runbook command failed: " + execution.getCommand()
            );
        }

        RunbookExecution saved = runbookExecutionRepository.save(execution);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RunbookExecutionResponse> getExecutions(UUID incidentId) {
        incidentRepository.findByIdAndNotDeleted(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        return runbookExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private RunbookExecutionResponse mapToResponse(RunbookExecution e) {
        return new RunbookExecutionResponse(
                e.getId(),
                e.getIncident().getId(),
                e.getInvestigationStep() != null ? e.getInvestigationStep().getId() : null,
                e.getCommand(),
                e.getExecutionStatus(),
                mapUser(e.getRequestedBy()),
                mapUser(e.getApprovedBy()),
                e.getOutput(),
                e.getErrorOutput(),
                e.getStartedAt(),
                e.getCompletedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private UserDto mapUser(User user) {
        if (user == null) return null;
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
